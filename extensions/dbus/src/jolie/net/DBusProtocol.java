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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import jolie.Interpreter;
import jolie.lang.parse.ast.types.UInt32;
import jolie.lang.parse.ast.types.UInt16;
import jolie.lang.parse.ast.types.UInt64;
import jolie.lang.parse.ast.InterfaceDefinition;
import jolie.net.protocols.ConcurrentCommProtocol;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.VariablePath;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.MessageProtocolVersionException;
import jolie.runtime.typing.JolieDBusUtils;
import org.freedesktop.dbus.Transport;
import org.freedesktop.dbus.Variant;
import jolie.net.dbus.Error;
import jolie.net.dbus.Message;
import jolie.net.dbus.MethodCall;
import jolie.net.dbus.MethodReturn;
import jolie.net.dbus.DBusSignal;
import jolie.net.ports.OutputPort;
import jolie.net.ports.Interface;
import jolie.runtime.InputOperation;
import jolie.runtime.InvalidIdException;
import jolie.runtime.typing.OneWayTypeDescription;
import jolie.runtime.typing.RequestResponseTypeDescription;
import jolie.runtime.typing.Type;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.OutputKeys;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.DocumentType;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import jolie.runtime.FaultException;


public class DBusProtocol extends ConcurrentCommProtocol {
    private HashMap<Long,MethodCall> _inMethodCalls = new HashMap<Long, MethodCall>(); 
    private HashMap<Long,MethodCall> _outMethodCalls = new HashMap<Long,MethodCall>();
    private HashMap<Long,CommMessage> _inCommMessage = new HashMap<Long, CommMessage>();
    private HashMap<Long,CommMessage> _outSerialMap = new HashMap<Long,CommMessage>();
    private HashMap<Long,Integer> _inMap = new HashMap<Long, Integer>();
        
    static final byte ENDIAN = Message.Endian.BIG;
    private final boolean _inputport;
    private final UInt32 _nameRequestFlags;
    private Interpreter _interperter;
    private final boolean _debug;
    private boolean _messageBus;
    private boolean _authenticated;
    private boolean _introspect;
    private byte[] _messageBody;
    private ArrayList<String> _rules;
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
       
        _introspect = checkBooleanParameter("introspect",true);
        
    }
   
    private Message readMessage(InputStream in)
            throws IOException {
        //read the 12 fixed bytes of the message header
        byte[] buf = new byte[12];
        byte[] tbuf;
        _messageBody = null;
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
        if (null == _messageBody) {
            _messageBody = new byte[bodyLength];
        }
        in.read(_messageBody, 0, _messageBody.length);

        /*parse body*/
        Object[] bodyObjects;
        //Value bodyObjectsValue;
        String sig = (String) dbusHeaders.get(new Byte(Message.HeaderField.SIGNATURE));
        if(sig != null && !sig.equals("")){
            try {
                bodyObjects = JolieDBusUtils.extract(sig, _messageBody, endian, new int[]{0, 0});
            } catch (DBusException e) {
                throw new IOException("Error parssing DBus message body : " + e.toString());
            }
        } else {
            bodyObjects = null;
        }
        /* create the comm message */

        String path = (String)dbusHeaders.get(Message.HeaderField.PATH);
        String iface = (String)dbusHeaders.get(Message.HeaderField.INTERFACE);
        String member = (String)dbusHeaders.get(Message.HeaderField.MEMBER); 
        String errorName = (String)dbusHeaders.get(Message.HeaderField.ERROR_NAME);
        UInt32 serial = (UInt32)dbusHeaders.get(Message.HeaderField.REPLY_SERIAL);
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
                try{
                    msg = new MethodCall(source,dest,path,iface,member,endian,flags,sig,bodyObjects);
                    _inMap.put(msg.getSerial(), senderCookie );

                } catch (DBusException de){
                    Interpreter.getInstance().logSevere("Error while parsing message : " + de.toString());
                }
                break;
            case Message.MessageType.METHOD_RETURN:
                if(_debug){
                    Interpreter.getInstance().logInfo("Message is a method return");
                }
                // get the outgoing methodCall.
                mc = _outMethodCalls.get(serial.longValue());
                try {
                    if(bodyObjects == null){
                        bodyObjects = new Object[0];
                    }
                    msg = new MethodReturn(mc,endian, sig, bodyObjects);
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
                    msg = new DBusSignal(source, path, iface, member,endian, sig, bodyObjects);
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
    public CommMessage recv(InputStream istream, OutputStream ostream)
            throws IOException {
        authenticate(istream,ostream);
        Message msg = readMessage(istream);
        CommMessage commMessage = null;
        if(msg instanceof MethodCall){
            if (_inputport && channel().parentPort().getInterface().containsOperation(msg.getName())) {
                try {
                    InputOperation operation = _interperter.getInputOperation(msg.getName());
                    Type requestType = operation.requestType();
                    Value bodyObjectsValue = Value.UNDEFINED_VALUE;
                    if (msg.getSig() != null && !msg.getSig().equals("")) {
                        bodyObjectsValue = JolieDBusUtils.extract(msg.getSig(), _messageBody, msg.getEndian(), new int[]{0, 0}, requestType);
                    }
                    commMessage = CommMessage.createRequest(msg.getName(), msg.getPath(), bodyObjectsValue);
                    _inMethodCalls.put(commMessage.id(), (MethodCall) msg);
                    _inCommMessage.put(commMessage.id(), commMessage);
                } catch (DBusException de) {
                    _interperter.logSevere(de);
                } catch (InvalidIdException iie) {
                    _interperter.logInfo("Recieved D-Bus method call for unsuported method " + iie);
                }
            } else {
                String output;
                // if the method and interface matches introspect then create automatic introspection information.
                if (_introspect && msg.getName().equals("Introspect") && msg.getInterface().equals("org.freedesktop.DBus.Introspectable")) {
                    try {
                        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                        
                        DOMImplementation domImpl = docBuilder.getDOMImplementation();
                        DocumentType doctype = domImpl.createDocumentType("node",
                        "-//freedesktop//DTD D-BUS Object Introspection 1.0//EN",
                        "http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd");
                        Document doc = docBuilder.newDocument();
                        
                        doc.appendChild(doctype);       
                        // root elements
                        Element rootElement = doc.createElement("node");
                        doc.appendChild(rootElement);
                        if (!_inputport) {
                            TreeMap<String, TreeMap<String,OneWayTypeDescription>> interfaces = new TreeMap<String,TreeMap<String,OneWayTypeDescription>>();
                            TreeMap<String,OneWayTypeDescription> ifaceMap;
                            // get the interface of the outputport.
                            Interface iface = channel().parentOutputPort().getInterface();
                            Map<String, OneWayTypeDescription> oneway = iface.oneWayOperations();
                            Iterator itr = oneway.entrySet().iterator();
                            while (itr.hasNext()) {
                                Map.Entry<String, OneWayTypeDescription> pair = (Map.Entry<String, OneWayTypeDescription>) itr.next();
                                InterfaceDefinition def = iface.interfaceForOperation(pair.getKey());
                                String ifaceName = def.name();
                                ifaceMap = interfaces.get(ifaceName);
                                if(ifaceMap != null){
                                    if(ifaceMap.get(pair.getKey()) == null){
                                      ifaceMap.put(pair.getKey(), pair.getValue());  
                                    }                                     
                                } else {
                                    TreeMap<String,OneWayTypeDescription> newInterface = new TreeMap<String, OneWayTypeDescription>();
                                    newInterface.put(pair.getKey(), pair.getValue());
                                    interfaces.put(ifaceName, newInterface);
                                }
                            }
                            for(Map.Entry<String,TreeMap<String,OneWayTypeDescription>> set : interfaces.entrySet()){
                                String interfaceName = set.getKey();
                                Element interfaceElement = doc.createElement("interface");
                                interfaceElement.setAttribute("name", interfaceName);
                                rootElement.appendChild(interfaceElement);
                                for(Map.Entry<String,OneWayTypeDescription> setItem : set.getValue().entrySet()){
                                    String signalName = setItem.getKey();
                                    Element signalElement = doc.createElement("signal");
                                    signalElement.setAttribute("name", signalName);
                                    Element argumentOut = doc.createElement("arg");
                                    try {
                                        argumentOut.setAttribute("type", getDBusSignature(setItem.getValue().requestType()));
                                    } catch (DBusException dbe){
                                        _interperter.logSevere(dbe);
                                    }
                                    signalElement.appendChild(argumentOut);
                                    interfaceElement.appendChild(signalElement);
                                }
                            }
                        } else {
                            //inputport
                            TreeMap<String, TreeMap<String,RequestResponseTypeDescription>> interfaces = new TreeMap<String,TreeMap<String,RequestResponseTypeDescription>>();
                            TreeMap<String,RequestResponseTypeDescription> ifaceMap;
                            Interface iface = channel().parentInputPort().getInterface();
                            Map<String, RequestResponseTypeDescription> rrDesc = iface.requestResponseOperations();
                            Iterator itr = rrDesc.entrySet().iterator();
                            while(itr.hasNext()){
                                Map.Entry<String,RequestResponseTypeDescription> pair = (Map.Entry<String,RequestResponseTypeDescription>) itr.next();
                                InterfaceDefinition def = iface.interfaceForOperation(pair.getKey());
                                String ifaceName = def.name();
                                ifaceMap = interfaces.get(ifaceName);
                                if(ifaceMap != null){
                                    if(ifaceMap.get(pair.getKey()) == null){
                                        ifaceMap.put(pair.getKey(),pair.getValue());
                                    }
                                } else {
                                    TreeMap<String,RequestResponseTypeDescription> newInterface = new TreeMap<String, RequestResponseTypeDescription>();
                                    newInterface.put(pair.getKey(), pair.getValue());
                                    interfaces.put(ifaceName, newInterface);
                                }
                            }
                            //create xml 
                            for(Map.Entry<String,TreeMap<String,RequestResponseTypeDescription>> set : interfaces.entrySet()){
                                String interfaceName = set.getKey();
                                Element interfaceElement = doc.createElement("interface");
                                interfaceElement.setAttribute("name", interfaceName);
                                rootElement.appendChild(interfaceElement);
                                for(Map.Entry<String,RequestResponseTypeDescription> setItem : set.getValue().entrySet()){
                                    String methodName = setItem.getKey();
                                    Element methodElement = doc.createElement("method");
                                    methodElement.setAttribute("name", methodName);
                                    Element argumentIn = doc.createElement("arg");
                                    Element argumentOut = doc.createElement("arg");
                                    try {
                                        argumentIn.setAttribute("type", getDBusSignature(setItem.getValue().requestType()));
                                        argumentIn.setAttribute("direction", "in");
                                        argumentOut.setAttribute("type", getDBusSignature(setItem.getValue().responseType()));
                                        argumentOut.setAttribute("direction", "out");
                                    } catch (DBusException dbe){
                                        _interperter.logSevere(dbe);
                                    }
                                    methodElement.appendChild(argumentIn);
                                    methodElement.appendChild(argumentOut);
                                    interfaceElement.appendChild(methodElement);
                                }
                                rootElement.appendChild(interfaceElement);
                            }
                        }
                        try{
                            TransformerFactory tf = TransformerFactory.newInstance();
                            Transformer transformer = tf.newTransformer();
                            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "-//freedesktop//DTD D-BUS Object Introspection 1.0//EN");
                            transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,"http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd");
                            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                            StringWriter writer = new StringWriter();
                            transformer.transform(new DOMSource(doc), new StreamResult(writer));
                            output = writer.getBuffer().toString();
                            
                            Value returnVal = Value.create(output);
                            CommMessage requestMessage = CommMessage.createRequest(msg.getName(), "/", null, "", Value.UNDEFINED_VALUE);
                            _inMethodCalls.put(requestMessage.id(), (MethodCall) msg);
                            _inCommMessage.put(requestMessage.id(), requestMessage);
                            
                            CommMessage returnMessage = CommMessage.createResponse(requestMessage, returnVal);
                            send(ostream, returnMessage, istream);
                            channel().disposeForInput();
                            commMessage = null;
                        } catch(TransformerException te){

                            _interperter.logSevere(te);
                        }
                    } catch (ParserConfigurationException pce) {
                        _interperter.logSevere(pce);
                    }
                }
                if (!(_introspect && msg.getName().equals("Introspect") && msg.getInterface().equals("org.freedesktop.DBus.Introspectable"))) {
                    _interperter.logInfo("Recieved D-Bus method call for unsuported method " + msg.getName());
                        Value bodyObjectsValue = Value.UNDEFINED_VALUE;
                        commMessage = CommMessage.createRequest(msg.getName(), msg.getPath(), bodyObjectsValue);
                        _inMethodCalls.put(commMessage.id(), (MethodCall) msg);
                        _inCommMessage.put(commMessage.id(), commMessage);
                        //send(ostream, CommMessage.createFaultResponse(commMessage, new FaultException( new FaultException( "IOException", "Invalid operation: " + commMessage.operationName() ))), istream);
                        //commMessage = CommMessage.createRequest("", "/", Value.UNDEFINED_VALUE);

                }                    

            }
        } else if(msg instanceof MethodReturn){
            // get the methodCall this is a Return for, and remove it from the outgoing method calls.
            MethodCall mc = _outMethodCalls.remove(msg.getReplySerial());
            if(mc!= null && channel().parentPort().getInterface().containsOperation(mc.getName())) {
                try {
                    OutputPort out = _interperter.getOutputPort(mc.getDestination());
                    RequestResponseTypeDescription rrTypeDescription = out.getInterface().requestResponseOperations().get(mc.getName());
                    Type responseType = rrTypeDescription.responseType();
                    Value bodyObjectsValue = Value.UNDEFINED_VALUE;
                    if(msg.getSig() != null && !msg.getSig().equals("")){
                        bodyObjectsValue = JolieDBusUtils.extract(msg.getSig(), _messageBody, msg.getEndian(), new int[]{0, 0},responseType);
                    }    
                    
                    // get the calling commMessage.
                    CommMessage requestMessage = _outSerialMap.remove(Long.valueOf(mc.getSerial()));
                    if(requestMessage != null){
                        commMessage = CommMessage.createResponse(requestMessage, bodyObjectsValue);
                    }
                
                } catch (DBusException de){
                    _interperter.logSevere(de);
                } catch (UnsupportedOperationException uoe){
                    _interperter.logSevere(uoe);
                } catch (InvalidIdException iee){
                    _interperter.logSevere(iee);
                }
            } else {
                 commMessage = CommMessage.createEmptyResponse(CommMessage.createRequest("", "/", Value.UNDEFINED_VALUE));
            }
        } else if(msg instanceof DBusSignal) {
             if (channel().parentPort().getInterface().containsOperation(msg.getName())) {
               try{
                    InputOperation operation = _interperter.getInputOperation(msg.getName());
                    Type requestType = operation.requestType();
                    Value bodyObjectsValue = Value.UNDEFINED_VALUE;
                    if(msg.getSig() != null && !msg.getSig().equals("")){
                        bodyObjectsValue = JolieDBusUtils.extract(msg.getSig(), _messageBody, msg.getEndian(), new int[]{0, 0},requestType);
                    } 
                    Interpreter.getInstance().getOneWayOperation(msg.getName());
                    commMessage = CommMessage.createRequest(msg.getName(), msg.getPath(), bodyObjectsValue);
                    // no storing of request since its a one-way and no return will be sent.
                } catch(InvalidIdException iie){
                  // ignore signal since the instance does not support a one-way message for this member/name 
                    _interperter.logInfo("Recieved D-Bus Signal for unsuported one-way method: " + iie);
                } catch (DBusException de) {
                    _interperter.logSevere(de);
                }
            } else {
                 commMessage = CommMessage.createRequest("", "/", Value.UNDEFINED_VALUE);
            }
        } else if(msg instanceof Error){
            MethodCall mc = _outMethodCalls.remove(msg.getReplySerial());
            CommMessage comm = _outSerialMap.remove(msg.getReplySerial());
            if(comm != null){
                try {
                    commMessage = CommMessage.createFaultResponse(comm, new FaultException(msg.getName(), (String)msg.getParameters()[0]));
                } catch (DBusException dbe){
                    _interperter.logSevere(dbe);
                }
            } else {
                // probably disapering service consumer that we have sent a reply/error to
                if(msg.getHeader(Message.HeaderField.ERROR_NAME).equals("org.freedesktop.DBus.Error.ServiceUnknown")){
                    try {
                       _interperter.logInfo("Service disapeared : " + msg.getParameters()[0].toString());
                       channel().disposeForInput();
                    } catch (DBusException dbe){
                        _interperter.logSevere(dbe);
                    }
                }
            }
        }
        return commMessage;
    }
   @Override
    public void send(OutputStream ostream, CommMessage message, InputStream istream)
            throws IOException {
        authenticate(istream, ostream);
        DataOutputStream oos = new DataOutputStream(ostream);
        String sig = getDBusSignature(message.value());
        Object[] objects = getObjectArray(message.value());
        Message msg = null;
        
        CommMessage jmc = _inCommMessage.remove(message.id());
        if(jmc != null) {
            // this is a MethodReturn. Get the original MethodCall
            MethodCall mc = _inMethodCalls.remove(jmc.id());
            
            
            //check to se if reply contains a fault
            if(message.isFault()){
                sig = getDBusSignature(message.fault().value());
                objects = getObjectArray(message.fault().value());
                try{
                    msg = new Error(mc.getSource(),this.channel().parentInputPort().name() + ".Error",_inMap.remove(mc.getSerial()), sig, objects);
                } catch(DBusException de) {
                    _interperter.logSevere(de);
                }
            } else {
            
                try{
                    msg = new MethodReturn(mc.getSource(), _inMap.remove(mc.getSerial()),Message.Endian.BIG, sig, objects);
                } catch(DBusException de) {
                    _interperter.logSevere(de);
                }
            }
        } else {
            //MethodCall or signal
            if(message.getDestination() != null){
                boolean signal = false;
                try{
                    OutputPort out = _interperter.getOutputPort(message.getDestination()); 
                    OneWayTypeDescription signalTest = out.getInterface().oneWayOperations().get(message.operationName());
                    if(signalTest != null)
                    {
                        signal = true;
                    }
                } catch (InvalidIdException iie) {
                    signal = false;
                }
                String resourcePath;
                if(hasParameter("object_path")){
                    resourcePath = getStringParameter("object_path");
                } else {
                    resourcePath = message.resourcePath();
                }
                try {
                    if(!signal){
                        msg = new MethodCall(message.getDestination(), resourcePath,
                            null, message.operationName(),Message.Endian.BIG, (byte) 0, sig, objects);
                        _outMethodCalls.put(msg.getSerial(), (MethodCall)msg);
                        _outSerialMap.put(msg.getSerial(), message);
                    } else {
                        // TODO add interface information. 
                        msg = new DBusSignal(null, resourcePath, message.getInterfaceDefinition().name(), message.operationName(),Message.Endian.BIG, sig, objects);
                    }
                } catch (DBusException dbe) {
                    _interperter.logSevere(dbe);
                }
            }// else probably garbage from incomming message/signal to an unsupported method. 
             
        }
        if (msg != null){
            for (byte[] buf : msg.getWireData()) {
                if (null == buf) {
                    break;
                }
                oos.write(buf);
            }
            if (message.isFault()){
                _interperter.logInfo("Send fault  to : " + msg.getDestination() +" with name :  "+  msg.getName() + " is error:" + message.isFault());
            }
        }
   }
    @Override 
    public void setup(InputStream istream, OutputStream ostream) throws IOException {
        if(_inputport){
            _messageBus = this.channel().parentInputPort().messageBus();
            
            if(getParameterVector("matchRules") != null){
                _rules = new ArrayList<String>();
                ValueVector vv = getValueVectorParameter("matchRules");
                if(vv != null){
                    Iterator itr = vv.iterator();
                    while(itr.hasNext()){
                        Object rule = itr.next();
                        if (rule instanceof Value){
                            _rules.add(((Value)rule).strValue());
                        }
                    }
                }
            } 
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
                comm = CommMessage.createRequest("RequestName", "/",null, "org.freedesktop.DBus", v);
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
                
                //MatchRule setup
                for(String rule : _rules){
                    v = Value.create(rule);
                    comm = CommMessage.createRequest("AddMatch", "/",null, "org.freedesktop.DBus", v);
                    send(ostream, comm, istream);
                    run = true;
                    while(run){
                        rply = readMessage(dis);
                        if(rply instanceof MethodReturn){
                            sig = rply.getSig();
                            if(sig != null && sig != ""){
                                try {
                                parameters = rply.getParameters();
                                    valueObject = createValueFromParam(parameters, sig);
                                } catch (DBusException de){
                                    _interperter.logSevere(de);
                                }
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
                
            }
            ostream.flush();
        } else if(!_authenticated){
            throw new IOException("DBus authentication failed");
        }
    }
    private void authenticate(InputStream istream, OutputStream ostream) throws IOException {
        if (!_authenticated) {
                if (!_messageBus && _inputport) {
                    _authenticated = dBusAuth(ostream, istream, true);
                } else {
                    /*input port but not connected to dbus-daemon, 
                     (setup method was not executed) */
                    _authenticated = dBusAuth(ostream, istream, false);
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
                        if (internalVal.hasChildren()) {
                                //children are part of a struct and should be kept as a array
                                internalArray.add(getObjectArray(internalVal));
                        } else if (internalVal.valueObject() != null) {
                            internalArray.add(internalVal.valueObject());
                        }
                    }
                    if (list == null) {
                        list = new ArrayList();
                    }
                    list.add(internalArray.toArray());
                } else if (vv.first().hasChildren()) {
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
    private Iterator getSubTypesIterator(Type t) throws DBusException {
        Set<Map.Entry<String,Type>> subTypes;
        List<Map.Entry<String,Type>> subTypesSorted = new LinkedList<Map.Entry<String,Type>>();

        java.lang.reflect.Method subTypesMethod;
        try{
            subTypesMethod = Type.class.getDeclaredMethod("subTypeSet");
            subTypesMethod.setAccessible(true);
            subTypes = (Set<Map.Entry<String,Type>>)subTypesMethod.invoke(t);
            
            subTypesSorted.addAll(subTypes);
            Collections.sort(subTypesSorted, new Comparator<Map.Entry<String,Type>>(){
                @Override
                public int compare(Map.Entry<String,Type> o1, Map.Entry<String,Type> o2) {
                    return o1.getKey().compareTo(o2.getKey());
                }
            });
            
        } catch(NoSuchMethodException nme){
            throw new DBusException(nme.getMessage());
        } catch (IllegalAccessException iae){
            throw new DBusException(iae.getMessage());
        } catch (InvocationTargetException ite){
            throw new DBusException(ite.getMessage());
        } 
        
        if(!subTypesSorted.isEmpty()){
            return subTypesSorted.iterator();
        } else {
            return null;
        }
    }
    
    private jolie.lang.NativeType getNativeType(Type t) throws DBusException{
        java.lang.reflect.Method nativeTypeMethod;
        jolie.lang.NativeType nativeType;
        try{
            nativeTypeMethod = Type.class.getDeclaredMethod("nativeType");
            nativeTypeMethod.setAccessible(true);
            nativeType = (jolie.lang.NativeType)nativeTypeMethod.invoke(t);
        } catch(NoSuchMethodException nme){
            throw new DBusException(nme.getMessage());
        } catch (IllegalAccessException iae){
            throw new DBusException(iae.getMessage());
        } catch (InvocationTargetException ite){
            throw new DBusException(ite.getMessage());
        } 
        
        return nativeType;
    }
    private jolie.util.Range getRange(Type t) throws DBusException{
        java.lang.reflect.Method rangeMethod;
        jolie.util.Range range;
        try{
            rangeMethod = Type.class.getDeclaredMethod("cardinality");
            rangeMethod.setAccessible(true);
            range = (jolie.util.Range)rangeMethod.invoke(t);
        } catch(NoSuchMethodException nme){
            throw new DBusException(nme.getMessage());
        } catch (IllegalAccessException iae){
            throw new DBusException(iae.getMessage());
        } catch (InvocationTargetException ite){
            throw new DBusException(ite.getMessage());
        } 
        
        return range;
    }
    
    private String getDBusSignature(Type t) throws DBusException{
        String sig = "";
        Iterator subItr = getSubTypesIterator(t);
        if(subItr == null){
            sig += getNativeTypeSignature(getNativeType(t));
        } else {
            while (subItr.hasNext()){
                Map.Entry<String,Type>subType = (Map.Entry<String,Type>)subItr.next(); 
                Iterator childrenIterator = getSubTypesIterator(subType.getValue());
                if(childrenIterator != null){
                    sig += "(";
                    sig += getDBusSignature(subType.getValue());
                    sig += ")";
                } else {
                    // no children
                    //check range for array type?
                    jolie.util.Range typeRange = getRange(subType.getValue());
                    if(typeRange.min() < typeRange.max()){
                        sig += "a";
                        sig += getDBusSignature(subType.getValue());
                    } else {
                        sig += getDBusSignature(subType.getValue());
                    }
                }
            }
        }
        return sig;
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
                } else if (vv.first().hasChildren()) {
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
                sig += "x";
            } else if (obj instanceof UInt32) {
                sig +="u";
            } else if (obj instanceof UInt16) {
                sig +="q";
            } else if (obj instanceof Short) {
                sig +="n";
            } else if (obj instanceof UInt64) {
                sig +="t";
            } else if (obj instanceof Byte) {
                sig +="y";
            }

        }
        return sig;
    }
    
    private String getNativeTypeSignature(jolie.lang.NativeType obj) {
        String sig = "";
        if (obj != null) {
            if (obj.id().equals("string")) {
                sig += "s";
            } else if (obj.id().equals("int")) {
                sig += "i";
            } else if (obj.id().equals("double")) {
                sig += "d";
            } else if (obj.id().equals("bool")) {
                sig += "b";
            } else if (obj.id().equals("long")) {
                sig += "x";
            } else if (obj.id().equals("uint32")) {
                sig +="u";
            } else if (obj.id().equals("uint16")) {
                sig +="q";
            } else if (obj.id().equals("int16")) {
                sig +="n";
            } else if (obj.id().equals("uint64")) {
                sig +="t";
            } else if (obj.id().equals("byte")) {
                sig +="y";
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
                    case 'x':
                        val = Value.create((Long) parameters[i]);
                        break;
                    case 'u':
                        UInt32 uint32 = (UInt32)parameters[i];
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
