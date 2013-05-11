/**
 * *************************************************************************
 * Copyright (C) by Tobias Mandrup Johansen * * This program is free software;
 * you can redistribute it and/or modify * it under the terms of the GNU Library
 * General Public License as * published by the Free Software Foundation; either
 * version 2 of the * License, or (at your option) any later version. * * This
 * program is distributed in the hope that it will be useful, * but WITHOUT ANY
 * WARRANTY; without even the implied warranty of * MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the * GNU General Public License for more
 * details. * * You should have received a copy of the GNU Library General
 * Public * License along with this program; if not, write to the * Free
 * Software Foundation, Inc., * 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA. * * For details about the authors of this software, see the
 * AUTHORS file. *
 **************************************************************************
 */
package jolie.net;

import cx.ath.matthew.unix.USOutputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import jolie.Interpreter;
import jolie.lang.Constants;
import jolie.lang.parse.ast.InterfaceDefinition;
import jolie.lang.parse.ast.OperationDeclaration;
import jolie.lang.parse.ast.types.UInt32;
import jolie.net.protocols.ConcurrentCommProtocol;
import jolie.runtime.AndJarDeps;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.VariablePath;
import jolie.util.Pair;
//import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.MessageProtocolVersionException;
import org.freedesktop.dbus.JolieDBusUtils;
/*import org.freedesktop.dbus.Message;
import org.freedesktop.dbus.MethodCall;
import org.freedesktop.dbus.MethodReturn;*/

import org.freedesktop.dbus.Transport;
import org.freedesktop.dbus.Variant;
import jolie.net.dbus.Error;
import jolie.net.dbus.Message;
import jolie.net.dbus.MethodCall;
import jolie.net.dbus.MethodReturn;
import jolie.net.dbus.DBusSignal;

public class DBusProtocol extends ConcurrentCommProtocol {

    private HashMap<Long, CommMessage> _commMessages = new HashMap<Long, CommMessage>();
    private HashMap<Long,String> _idMap = new HashMap<Long,String>();
    private HashMap<String,MethodCall> _incommingCalls = new HashMap<String,MethodCall>();
    private HashMap<Long,MethodCall> _outgoingCalls = new HashMap<Long,MethodCall>();
    private HashMap<String,Long> _dbusSerials = new HashMap<String, Long>();
    private HashMap<Long,Long> _outgoingIds = new HashMap<Long, Long>();
    static final byte ENDIAN = Message.Endian.BIG;
    private final boolean _inputport;
    private final UInt32 _nameRequestFlags;
    private Interpreter _interperter;
    private final boolean _debug;
    private boolean _messageBus;
    private boolean _authenticated; 
    @Override
    public String name() {
        return "dbus";
    }

    public DBusProtocol(VariablePath configurationPath,boolean inputport) {
        super(configurationPath);
        _inputport = inputport;
        _debug = checkBooleanParameter("debug");
        _interperter = Interpreter.getInstance();
        _nameRequestFlags = getUInt32Parameter("nrFlags");
    }
   
    private Message readMessage(InputStream in)
            throws IOException {
        //read the 12 fixed bytes of the message header
        byte[] buf = new byte[12];
        byte[] tbuf;
        byte[] body = null;
        HashMap<Byte, Object> dbusHeaders = new HashMap<Byte, Object>();
        in.read(buf);

        /* Parse the details from the header */
        byte endian = buf[0];
        byte messageType = buf[1];
        byte flags = buf[2];
        byte version = buf[3];
        int bodyLength = (int) Message.demarshallint(buf, 4, endian, 4);
        int senderCookie = (int) Message.demarshallint(buf, 8, endian, 4);
        
        if(_debug){
            Interpreter i = Interpreter.getInstance();
            i.logInfo("Reading Message" +
                    "\n endian : " + endian +
                    "\n messageType : " + messageType +
                    "\n flags : " + flags +
                    "\n version : " + version +
                    "\n bodyLength : " + bodyLength +
                    "\n senderCookie : " + senderCookie);
        }

        // check protocol version.
        if (version > Message.PROTOCOL) {
            throw new MessageProtocolVersionException(MessageFormat.format("Protocol version {0} is unsupported", new Object[]{version}));
        }

        /* Read the length of the variable header */
        tbuf = new byte[4];
        in.read(tbuf);

        /* Parse the variable header length */
        int headerlen;
        headerlen = (int) Message.demarshallint(tbuf, 0, endian, 4);
        if (0 != headerlen % 8) {
            headerlen += 8 - (headerlen % 8);
        }

        /* Read the variable header */
        byte[] headers;
        headers = new byte[headerlen + 8];
        //copy the length of the variable header into header (4 bytes)
        // the next 4 bytes will be empty/padding
        System.arraycopy(tbuf, 0, headers, 0, 4);
        //read headerlength bytes into header starting with offset of 8 bytes.
        in.read(headers, 8, headerlen);

        /* parse variable headers */
        Object[] headerObjects;
        try {
            headerObjects = JolieDBusUtils.extract("a(yv)", headers, endian, new int[]{0, 0});
            for (Object o : (Vector<Object>) headerObjects[0]) {
                dbusHeaders.put((Byte) ((Object[]) o)[0], ((Variant<Object>) ((Object[]) o)[1]).getValue());
            }
        } catch (DBusException e) {
            throw new IOException("Error parssing DBus variable headers : " + e.toString());
        }

        /* read body */
        if (null == body) {
            body = new byte[bodyLength];
        }
        in.read(body, 0, body.length);

        /*parse body*/
        Object[] bodyObjects;
        String sig = (String) dbusHeaders.get(new Byte(Message.HeaderField.SIGNATURE));
        try {
            bodyObjects = JolieDBusUtils.extract(sig, body, endian, new int[]{0, 0});
        } catch (DBusException e) {
            throw new IOException("Error parssing DBus message body : " + e.toString());
        }

        /* create the comm message */
        //CommMessage cm = null;
        String path = (String)dbusHeaders.get(Message.HeaderField.PATH);
        String iface = (String)dbusHeaders.get(Message.HeaderField.INTERFACE);
        String member = (String)dbusHeaders.get(Message.HeaderField.MEMBER); 
        String errorName = (String)dbusHeaders.get(Message.HeaderField.ERROR_NAME);
        org.freedesktop.dbus.UInt32 serial = (org.freedesktop.dbus.UInt32)dbusHeaders.get(Message.HeaderField.REPLY_SERIAL);
        String dest = (String)dbusHeaders.get(Message.HeaderField.DESTINATION);
        String source = (String)dbusHeaders.get(Message.HeaderField.SENDER);
        
        Message msg = null;
        MethodCall mc = null;
        Long commId;
        switch (messageType) {
            case Message.MessageType.METHOD_CALL:
                if(_debug){
                    Interpreter.getInstance().logInfo("Message is a method call");
                }
                try {
                    msg = new MethodCall(source,dest,path,iface,member,flags,sig,bodyObjects);
                    _incommingCalls.put(source,(MethodCall)msg);
                    _dbusSerials.put(source,new Long(senderCookie));
                } catch (DBusException dbe) {
                    if(_debug){
                        Interpreter.getInstance().logSevere("Error while parsing message : " + dbe.toString());
                    }
                }
                break;
            case Message.MessageType.METHOD_RETURN:
                if(_debug){
                    Interpreter.getInstance().logInfo("Message is a method return");
                }
                mc = _outgoingCalls.get(serial.longValue());
                //mc = _calls.get(serial.longValue());
                try {
                    msg = new MethodReturn(mc, sig, bodyObjects);
                } catch(DBusException de) {
                    _interperter.logSevere(de);
                }
                break;
            case Message.MessageType.SIGNAL:
                if(_debug){
                    _interperter.logInfo("Message is a signal");
                }
                if(iface == null || member == null || path == null){
                    _interperter.logWarning("Invalid signal recieved : " +
                                            "\n interface : " + iface +
                                            "\n member : " + member + 
                                            "\n path : " + path);
                }
                try {
                    msg = new DBusSignal(source, path, iface, member, sig, bodyObjects);
                } catch (DBusException de){
                    _interperter.logSevere(de);
                }
                break;
            case Message.MessageType.ERROR:
                if(_debug){
                    Interpreter.getInstance().logInfo("Message is an error");
                }
                try {
                    msg = new Error(dest, errorName, serial.longValue(), sig, bodyObjects);
                } catch (DBusException de){
                    _interperter.logSevere(de);
                }
                break;
        }
        return msg;
    }

    @Override
    public void send(OutputStream ostream, CommMessage message, InputStream istream)
            throws IOException {
        authenticate(istream, ostream);
        DataOutputStream oos = new DataOutputStream(ostream);
        String sig = getDBusSignature(message.value());
        Object[] objects = getObjectArray(message.value());
        Message msg = null;
        if (_commMessages.get(message.id()) != null) {
            if (message.getLocation() != null){
                try {
                    // this is a MethodReturn, previously a recieved MethodCall has occured.
                    String source = _idMap.get(message.id());
                    MethodCall mc = _incommingCalls.get(source);
                    Long serial = _dbusSerials.get(source);
                    msg = new MethodReturn(source,serial, sig, objects);
                } catch (DBusException DBe) {
                    throw new IOException("Error while writing message : " + DBe.toString());
                }
            } else {
                // this is a one-way "acknowledgement". ignore.
                //and remove from _commMessages 
                _commMessages.remove(message.id());
                _idMap.remove(message.id());
            }
        } else {
            // deal with methodcalls & signals?
            if(message.getLocation() == null){
                //signal? or notification
                // if signal then PATH, MEMEBER & INTERFACE is required.
                //interface
                //what to do with it?
            } else {
            
                try {
                    msg = new MethodCall(message.getLocation(), message.resourcePath(),
                            null, message.operationName(), (byte) 0, sig, objects);
                    _commMessages.put(message.id(), message);
                    _outgoingIds.put(msg.getSerial(),message.id());
                    _outgoingCalls.put(msg.getSerial(),(MethodCall)msg);
                } catch (DBusException dbe) {
                    throw new IOException("DBus threw an exception : " + dbe);
                }
            }
        }
        if (msg != null){
            for (byte[] buf : msg.getWireData()) {
                if (null == buf) {
                    break;
                }
                oos.write(buf);
            }
        }
    }
    @Override
    public CommMessage recv(InputStream istream, OutputStream ostream)
            throws IOException {
        authenticate(istream,ostream);
        Message msg = readMessage(istream);
        CommMessage commMessage = null;
        if(msg instanceof MethodCall){
            try {
                commMessage = CommMessage.createRequest(msg.getName(), msg.getPath(), createValueFromParam(msg.getParameters(), msg.getSig()));
                _commMessages.put(commMessage.id(), commMessage);
                _idMap.put(commMessage.id(), msg.getSource());
            } catch (DBusException de ){
                _interperter.logSevere(de);
            } 
        } else if(msg instanceof MethodReturn){
            try {
                Long serial = _outgoingIds.get(msg.getReplySerial());
                CommMessage requestMessage = _commMessages.get(serial);
                commMessage = CommMessage.createResponse(requestMessage, createValueFromParam(msg.getParameters(), msg.getSig()));
            } catch (DBusException de){
                _interperter.logSevere(de);
            } 
        } else if(msg instanceof DBusSignal) {
            try{
                commMessage = CommMessage.createRequest(msg.getName(), msg.getPath(), createValueFromParam(msg.getParameters(), msg.getSig()));
                _commMessages.put(commMessage.id(), commMessage);
                _idMap.put(commMessage.id(), msg.getSource());
            } catch (DBusException de) {
                _interperter.logSevere(de);
            }
        } else if(msg instanceof Error){
            _interperter.logSevere(((Error)msg).getName());
        }
        return commMessage;
    }
    @Override 
    public void setup(InputStream istream, OutputStream ostream) throws IOException {
        if(_inputport){
            _messageBus = this.channel().parentInputPort().messageBus();
        } else {
            _messageBus = this.channel().parentOutputPort().messageBus();
        }
        authenticate(istream, ostream);
        //Say hello to the server
        if(_authenticated){
            DataInputStream dis = new DataInputStream(istream);
            CommMessage comm = CommMessage.createRequest("Hello","/",null,"org.freedesktop.DBus", Value.UNDEFINED_VALUE);
            Message rply = null;
            send(ostream, comm, istream);
            Object[] parameters = null;
            String sig = null;
            Value valueObject = null;
            ostream.flush();
            //listen for reply
            boolean run = true;
            while(run){
                rply = readMessage(dis);
                if(rply instanceof MethodReturn){
                    sig = rply.getSig();
                    try {
                        parameters = rply.getParameters();
                        valueObject = createValueFromParam(parameters, sig);
                    } catch (DBusException de){
                        _interperter.logSevere(de);
                    }
                    _interperter.logInfo("Recieved methodReturn" + "\n Signature : " + sig);
                    run = false;
                } else if(rply instanceof DBusSignal) {
                    // there might be a name aquired signal
                    _interperter.logInfo("Recieved signal with name : " + rply.getName());
                    // continue reading untill methodReturn
                } else if(rply instanceof Error) {
                    _interperter.logInfo("Recieved Error with name : " + rply.getName());
                    // continue reading untill methodReturn
                } else if(rply instanceof MethodCall) {
                    _interperter.logInfo("Recieved methodCall with name : " + rply.getName());
                    // continue reading untill methodReturn
                }
            }
            if(_messageBus && _inputport){
                Value v = Value.create();
                ValueVector vv = ValueVector.create();
                if(_nameRequestFlags != null){
                    vv.add(Value.create(_nameRequestFlags));
                } else {
                    vv.add(Value.create(new UInt32(0L)));
                }
                v.children().put("p2", vv);
                vv = ValueVector.create();
                vv.add(Value.create(this.channel().parentInputPort().name()));
                v.children().put("p1", vv);
                comm = CommMessage.createRequest("RequestName", "/", null, "org.freedesktop.DBus", v);
                send(ostream, comm, istream);
                run = true;
                while(run){
                    rply = readMessage(dis);
                    if(rply instanceof MethodReturn){
                        sig = rply.getSig();
                        try {
                            parameters = rply.getParameters();
                            valueObject = createValueFromParam(parameters, sig);
                        } catch (DBusException de){
                            _interperter.logSevere(de);
                        }
                        _interperter.logInfo("Recieved methodReturn" + "\n signature : " + sig);
                        run = false;
                    } else if(rply instanceof DBusSignal) {
                        // there might be a name aquired signal
                        _interperter.logInfo("Recieved signal with name : " + rply.getName());
                        // read more messages 
                    } else if(rply instanceof Error) {
                        _interperter.logInfo("Recieved Error with name : " + rply.getName());
                        // continue reading untill methodReturn
                    } else if(rply instanceof MethodCall) {
                        _interperter.logInfo("Recieved methodCall with name : " + rply.getName());
                        // continue reading untill methodReturn
                    }
                }
            }
            ostream.flush();
        } else if(!_authenticated){
            throw new IOException("DBus authentication failed");
        }
    }
    private void authenticate(InputStream istream, OutputStream ostream) throws IOException {
        if (!_authenticated) {
            if (!hasParameter("authenticated")) {
                if (!_messageBus && _inputport) {
                    _authenticated = dBusAuth(ostream, istream, true);
                } else {
                    /*input port but not connected to dbus-daemon, 
                     (setup method was not executed) */
                    _authenticated = dBusAuth(ostream, istream, false);
                }
                configurationPath().getValue().getNewChild("authenticated").setValue(_authenticated);

            } else {
                _authenticated = checkBooleanParameter("authenticated");
                if (!_authenticated) {
                    if (!_messageBus && _inputport) {
                        _authenticated = dBusAuth(ostream, istream, true);
                    } else {
                        _authenticated = dBusAuth(ostream, istream, false);
                    }
                    configurationPath().getValue().getFirstChild("authenticated").setValue(_authenticated);
                } else {
                    Interpreter.getInstance().logInfo("Already Authenticated : true");
                }
            }
        }
    }
    private boolean dBusAuth(OutputStream ostream, InputStream istream, boolean server) throws IOException {
            //when authenticating using the SASL method, the guid has to be filled if acting as dbus server,
            //this should probably be parsed from the URI env variable. Otherwise the guid can be left blank. 

            //TODO : fix bug, when using "normal" socket and not a local this will throw an exception because 
            // USOutputStream class has not been loaded by the class loader.
            boolean authenticated;
            if (ostream instanceof USOutputStream) {
                USOutputStream usos = (USOutputStream) ostream;
                if (!server) {
                    if (new Transport.SASL().auth(Transport.SASL.MODE_CLIENT,
                            Transport.SASL.AUTH_EXTERNAL,
                            "",
                            ostream,
                            istream,
                            usos.getSocket())) {
                        authenticated = true;
                        //set credentials 
                        usos.getSocket().setPassCred(true);
                    } else {
                        authenticated = false;
                        throw new IOException("Failed to authenticate");
                    }
                } else {
                    if (new Transport.SASL().auth(Transport.SASL.MODE_SERVER,
                            Transport.SASL.AUTH_EXTERNAL,
                            null, //TODO fix this.
                            ostream,
                            istream,
                            usos.getSocket())) {
                        //set credentials 
                        usos.getSocket().setPassCred(true);
                        authenticated = true;
                    } else {
                        authenticated = false;
                        throw new IOException("Failed to authenticate.");
                    }
                }
            } else {
                authenticated = false;
                throw new IOException("DBus currently only support unix sockets.");
                //check if osstream is a instance of a normal tcp socket connection
                //TODO: implement if TCP stream.
            }
        return authenticated;
    }
    private Object[] getObjectArray(Value val) {
        ArrayList list = new ArrayList();
        Map<String, ValueVector> map = new TreeMap(val.children());
        if (!map.isEmpty()) {
            for (Map.Entry<String, ValueVector> entry : map.entrySet()) {
            //Collection<ValueVector> values = map.values();
            //for (ValueVector vv : values) {
                ValueVector vv = entry.getValue();
                //array type if more then 1?
                if (vv.size() > 1) {
                    ArrayList internalArray = new ArrayList();
                    for (int i = 0; i < vv.size(); i++) {
                        Value internalVal = vv.get(i);
                        if (!val.children().isEmpty()) {
                            if (internalVal.hasChildren() && internalVal.children().size() > 1) {
                                //children are part of a struct and should be kept as a array
                                internalArray.add(getObjectArray(internalVal));
                            } else {
                                internalArray.add(getObjectArray(internalVal));
                            }
                        } else if (internalVal.valueObject() != null) {
                            internalArray.add(internalVal.valueObject());
                        }
                    }
                    if (list == null) {
                        list = new ArrayList();
                    }
                    list.add(internalArray.toArray());
                } else if (vv.first().children().size() > 1) {
                    list.add(getObjectArray(vv.first()));
                } else {
                    list.add(vv.first().valueObject());
                }
            }
        } else {
            list.add(val.valueObject());
        }
        return list.toArray();
    }
    /**
     * Gets the dbus signature of a value from Jolie
     *
     * @param val the Jolie value implementation object.
     * @return a signature string corresponding to the Jolie Value object.
     */
    private String getDBusSignature(Value val) {
        String sig = "";
        Map<String, ValueVector> map = new TreeMap(val.children());
        if (!map.isEmpty()) {
            for (Map.Entry<String, ValueVector> entry : map.entrySet()) {
            //Collection<ValueVector> values = map.values();
            //for (ValueVector vv : values) {
                //array type if more then 1?
            ValueVector vv = entry.getValue();
                if (vv.size() > 1) {
                    Value internalVal = vv.first();
                    if (!val.children().isEmpty()) {
                        sig += "a";
                        if (internalVal.hasChildren() && internalVal.children().size() > 1) {
                            sig += "(";
                            sig += getDBusSignature(internalVal);
                            sig += ")";
                        } else {
                            sig += getDBusSignature(internalVal);
                        }
                    } else if (internalVal.valueObject() != null) {
                        sig += getDBusValueObjectSignature(internalVal.valueObject());
                    }
                } else if (vv.first().children().size() > 1) {
                    sig += "(";
                    sig += getDBusSignature(vv.first());
                    sig += ")";
                } else {
                    sig += getDBusValueObjectSignature(vv.first().valueObject());
                }
            }
        } else {
            sig += getDBusValueObjectSignature(val.valueObject());
        }
        return sig;
    }
    private String getDBusValueObjectSignature(Object obj) {
        String sig = "";
        if (obj != null) {
            if (obj instanceof String) {
                sig += "s";
            } else if (obj instanceof Integer) {
                sig += "i";
            } else if (obj instanceof Double) {
                sig += "d";
            } else if (obj instanceof Boolean) {
                sig += "b";
            } else if (obj instanceof Long) {
                sig += "t";
            } else if (obj instanceof UInt32) {
                sig +="u";
            }

        }
        return sig;
    }
    /**
     * Create a Jolie value object with data from D-Bus.
     *
     * @param parameter the object array from DBus.
     * @param signature the signature of the parameter objects.
     * @return
     */
    private Value createValueFromParam(Object[] parameters, String signature) {
        Value root = null;
        Value val = null;
        if (signature != null) {
            for (int i = 0; i < signature.length(); i++) {
                char sig = signature.charAt(i);
                switch (sig) {
                    case 's':
                        val = Value.create((String) parameters[i]);
                        break;
                    case 'i':
                        val = Value.create((Integer) parameters[i]);
                        break;
                    case 'd':
                        val = Value.create((Double) parameters[i]);
                        break;
                    case 'b':
                        val = Value.create((Boolean) parameters[i]);
                        break;
                    case 't':
                        val = Value.create((Long) parameters[i]);
                        break;
                    case 'u':
                        org.freedesktop.dbus.UInt32 uint32 = (org.freedesktop.dbus.UInt32)parameters[i];
                        val = Value.create(new UInt32(uint32.longValue()));
                        break;
                    default:
                        break;
                }
                if (root == null) {
                    root = val;
                } else {
                    root.add(val);
                }
            }
            return root;
        } else {
            return null;
        }
    }
}
