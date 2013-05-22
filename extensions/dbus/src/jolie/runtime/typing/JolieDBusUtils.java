/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
   
   Modified to suit Jolie/DBus protocol implementation by Tobias Mandrup Johansen

*/
package jolie.runtime.typing;

import cx.ath.matthew.debug.Debug;
import cx.ath.matthew.utils.Hexdump;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import jolie.lang.parse.ast.types.UInt32;
import jolie.lang.parse.ast.types.UInt16;
import jolie.lang.parse.ast.types.UInt64;
import jolie.net.dbus.Marshalling;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.MarshallingException;
import org.freedesktop.dbus.exceptions.UnknownTypeCodeException;
import jolie.net.dbus.Message;
import org.freedesktop.dbus.AbstractConnection;
import org.freedesktop.dbus.Variant;

public class JolieDBusUtils {
    
    static final int MAX_ARRAY_LENGTH = 67108864;
    static final int MAX_NAME_LENGTH = 255; 
    /**
     * Demarshall values from a buffer.
     *
     * @param sig The D-Bus signature(s) of the value(s).
     * @param buf The buffer to demarshall from.
     * @param ofs An array of two ints, the offset into the signature and the
     * offset into the data buffer. These values will be updated to the start of
     * the next value ofter demarshalling.
     * @return The demarshalled value(s).
     */
    public static Object[] extract(String sig, byte[] buf, byte endian, int[] ofs) throws DBusException {
        if (Debug.debug) {
            Debug.print(Debug.VERBOSE, "extract(" + sig + ",#" + buf.length + ", {" + ofs[0] + "," + ofs[1] + "}");
        }
        //System.out.println("extract(" + sig + ",#" + buf.length + ", {" + ofs[0] + "," + ofs[1] + "}");
        Vector<Object> rv = new Vector<Object>();
        byte[] sigb = sig.getBytes();
        for (int[] i = ofs; i[0] < sigb.length; i[0]++) {
            rv.add(extractone(sigb, buf, endian, i, false));
        }
        return rv.toArray();
    }
        /**
     * Demarshall one value from a buffer.
     * @param sigb A buffer of the D-Bus signature.
     * @param buf The buffer to demarshall from.
     * @param ofs An array of two ints, the offset into the signature buffer and
     * the offset into the data buffer. These values will be updated to the
     * start of the next value ofter demarshalling.
     * @param contained converts nested arrays to Lists
     * @return The demarshalled value.
     */
    private static Object extractone(byte[] sigb, byte[] buf, byte endian, int[] ofs, boolean contained) throws DBusException {
        if (Debug.debug) {
            Debug.print(Debug.VERBOSE, "Extracting type: " + ((char) sigb[ofs[0]]) + " from offset " + ofs[1]);
        }
        //System.out.println("Extracting type: " + ((char) sigb[ofs[0]]) + " from offset " + ofs[1]);

        Object rv = null;
        ofs[1] = align(ofs[1], sigb[ofs[0]]);
        switch (sigb[ofs[0]]) {
            case Message.ArgumentType.BYTE:
                rv = buf[ofs[1]++];
                break;
            case Message.ArgumentType.UINT32:
                rv = new UInt32(Message.demarshallint(buf, ofs[1], endian, 4));
                ofs[1] += 4;
                break;
            case Message.ArgumentType.INT32:
                rv = (int) Message.demarshallint(buf, ofs[1], endian, 4);
                ofs[1] += 4;
                break;
            case Message.ArgumentType.INT16:
                rv = (short) Message.demarshallint(buf, ofs[1], endian, 2);
                ofs[1] += 2;
                break;
            case Message.ArgumentType.UINT16:
                rv = new UInt16((int) Message.demarshallint(buf, ofs[1], endian, 2));
                ofs[1] += 2;
                break;
            case Message.ArgumentType.INT64:
                rv = Message.demarshallint(buf, ofs[1], endian, 8);
                ofs[1] += 8;
                break;
            case Message.ArgumentType.UINT64:
                long top;
                long bottom;
                if (endian == Message.Endian.BIG) {
                    top = Message.demarshallint(buf, ofs[1], endian, 4);
                    ofs[1] += 4;
                    bottom = Message.demarshallint(buf, ofs[1], endian, 4);
                } else {
                    bottom = Message.demarshallint(buf, ofs[1], endian, 4);
                    ofs[1] += 4;
                    top = Message.demarshallint(buf, ofs[1], endian, 4);
                }
                rv = new UInt64(top, bottom);
                ofs[1] += 4;
                break;
            case Message.ArgumentType.DOUBLE:
                long l = Message.demarshallint(buf, ofs[1], endian, 8);
                ofs[1] += 8;
                rv = Double.longBitsToDouble(l);
                break;
            case Message.ArgumentType.FLOAT:
                int rf = (int) Message.demarshallint(buf, ofs[1], endian, 4);
                ofs[1] += 4;
                rv = Float.intBitsToFloat(rf);
                break;
            case Message.ArgumentType.BOOLEAN:
                rf = (int) Message.demarshallint(buf, ofs[1], endian, 4);
                ofs[1] += 4;
                rv = (1 == rf) ? Boolean.TRUE : Boolean.FALSE;
                break;
            case Message.ArgumentType.ARRAY:
                long size = Message.demarshallint(buf, ofs[1], endian, 4);
                if (Debug.debug) {
                    Debug.print(Debug.VERBOSE, "Reading array of size: " + size);
                }
                //System.out.println("Reading array of size: " + size);
                ofs[1] += 4;
                byte algn = (byte) Message.getAlignment(sigb[++ofs[0]]);
                ofs[1] = align(ofs[1], sigb[ofs[0]]);
                int length = (int) (size / algn);
                if (length > MAX_ARRAY_LENGTH) {
                    throw new MarshallingException("Arrays must not exceed " + MAX_ARRAY_LENGTH);
                    // optimise primatives
                }
                switch (sigb[ofs[0]]) {
                    case Message.ArgumentType.BYTE:
                        rv = new byte[length];
                        System.arraycopy(buf, ofs[1], rv, 0, length);
                        ofs[1] += size;
                        break;
                    case Message.ArgumentType.INT16:
                        rv = new short[length];
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            ((short[]) rv)[j] = (short) Message.demarshallint(buf, ofs[1], endian, algn);
                        }
                        break;
                    case Message.ArgumentType.INT32:
                        rv = new int[length];
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            ((int[]) rv)[j] = (int) Message.demarshallint(buf, ofs[1], endian, algn);
                        }
                        break;
                    case Message.ArgumentType.INT64:
                        rv = new long[length];
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            ((long[]) rv)[j] = Message.demarshallint(buf, ofs[1], endian, algn);
                        }
                        break;
                    case Message.ArgumentType.BOOLEAN:
                        rv = new boolean[length];
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            ((boolean[]) rv)[j] = (1 == Message.demarshallint(buf, ofs[1], endian, algn));
                        }
                        break;
                    case Message.ArgumentType.FLOAT:
                        rv = new float[length];
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            ((float[]) rv)[j] = Float.intBitsToFloat((int) Message.demarshallint(buf, ofs[1], endian, algn));
                        }
                        break;
                    case Message.ArgumentType.DOUBLE:
                        rv = new double[length];
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            ((double[]) rv)[j] = Double.longBitsToDouble(Message.demarshallint(buf, ofs[1], endian, algn));
                        }
                        break;
                    case Message.ArgumentType.DICT_ENTRY1:
                        if (0 == size) {
                            // advance the type parser even on 0-size arrays.
                            Vector<java.lang.reflect.Type> temp = new Vector<java.lang.reflect.Type>();
                            byte[] temp2 = new byte[sigb.length - ofs[0]];
                            System.arraycopy(sigb, ofs[0], temp2, 0, temp2.length);
                            String temp3 = new String(temp2);
                            // ofs[0] gets incremented anyway. Leave one character on the stack
                            int temp4 = Marshalling.getJavaType(temp3, temp, 1) - 1;
                            ofs[0] += temp4;
                            if (Debug.debug) {
                                Debug.print(Debug.VERBOSE, "Aligned type: " + temp3 + " " + temp4 + " " + ofs[0]);
                            }
                            //System.out.println("Aligned type: " + temp3 + " " + temp4 + " " + ofs[0]);
                        }
                        int ofssave = ofs[0];
                        long end = ofs[1] + size;
                        Vector<Object[]> entries = new Vector<Object[]>();
                        while (ofs[1] < end) {
                            ofs[0] = ofssave;
                            entries.add((Object[]) extractone(sigb, buf, endian, ofs, true));
                        }
                        //rv = new DBusMap<Object, Object>(entries.toArray(new Object[0][]));
                        break;
                    default:
                        if (0 == size) {
                            // advance the type parser even on 0-size arrays.
                            Vector<java.lang.reflect.Type> temp = new Vector<java.lang.reflect.Type>();
                            byte[] temp2 = new byte[sigb.length - ofs[0]];
                            System.arraycopy(sigb, ofs[0], temp2, 0, temp2.length);
                            String temp3 = new String(temp2);
                            // ofs[0] gets incremented anyway. Leave one character on the stack
                            int temp4 = Marshalling.getJavaType(temp3, temp, 1) - 1;
                            ofs[0] += temp4;
                            if (Debug.debug) {
                                Debug.print(Debug.VERBOSE, "Aligned type: " + temp3 + " " + temp4 + " " + ofs[0]);
                            }
                            //System.out.println("Aligned type: " + temp3 + " " + temp4 + " " + ofs[0]);
                        }
                        ofssave = ofs[0];
                        end = ofs[1] + size;
                        Vector<Object> contents = new Vector<Object>();
                        while (ofs[1] < end) {
                            ofs[0] = ofssave;
                            contents.add(extractone(sigb, buf, endian, ofs, true));
                        }
                        rv = contents;
                }
                if (contained && !(rv instanceof List) && !(rv instanceof Map)) {
                    //rv = ArrayFrob.listify(rv);
                }
                break;
            case Message.ArgumentType.STRUCT1:
                Vector<Object> contents = new Vector<Object>();
                while (sigb[++ofs[0]] != Message.ArgumentType.STRUCT2) {
                    contents.add(extractone(sigb, buf, endian, ofs, true));
                }
                rv = contents.toArray();
                break;
            case Message.ArgumentType.DICT_ENTRY1:
                Object[] decontents = new Object[2];
                if (Debug.debug) {
                    Debug.print(Debug.VERBOSE, "Extracting Dict Entry (" + Hexdump.toAscii(sigb, ofs[0], sigb.length - ofs[0]) + ") from: " + Hexdump.toHex(buf, ofs[1], buf.length - ofs[1]));
                }
                //System.out.println("Extracting Dict Entry (" + Hexdump.toAscii(sigb, ofs[0], sigb.length - ofs[0]) + ") from: " + Hexdump.toHex(buf, ofs[1], buf.length - ofs[1]));
                ofs[0]++;
                decontents[0] = extractone(sigb, buf, endian, ofs, true);
                ofs[0]++;
                decontents[1] = extractone(sigb, buf, endian, ofs, true);
                ofs[0]++;
                rv = decontents;
                break;
            case Message.ArgumentType.VARIANT:
                int[] newofs = new int[]{0, ofs[1]};
                String sig = (String) extract(Message.ArgumentType.SIGNATURE_STRING, buf, endian, newofs)[0];
                newofs[0] = 0;
                rv = new Variant<Object>(extract(sig, buf, endian, newofs)[0], sig);
                ofs[1] = newofs[1];
                break;
            case Message.ArgumentType.STRING:
                length = (int) Message.demarshallint(buf, ofs[1], endian, 4);
                ofs[1] += 4;
                try {
                    rv = new String(buf, ofs[1], length, "UTF-8");
                } catch (UnsupportedEncodingException UEe) {
                    if (AbstractConnection.EXCEPTION_DEBUG && Debug.debug) {
                        Debug.print(UEe);
                    }
                    throw new DBusException("System does not support UTF-8 encoding");
                }
                ofs[1] += length + 1;
                break;
            case Message.ArgumentType.OBJECT_PATH:
                length = (int) Message.demarshallint(buf, ofs[1], endian, 4);
                ofs[1] += 4;
                //rv = new ObjectPath(getSource(), new String(buf, ofs[1], length));
                rv = new String(buf, ofs[1], length);
                ofs[1] += length + 1;
                break;
            case Message.ArgumentType.SIGNATURE:
                length = (buf[ofs[1]++] & 0xFF);
                rv = new String(buf, ofs[1], length);
                ofs[1] += length + 1;
                break;
            default:
                throw new UnknownTypeCodeException(sigb[ofs[0]]);
        }
        if (Debug.debug) {
            if (rv instanceof Object[]) {
                Debug.print(Debug.VERBOSE, "Extracted: " + Arrays.deepToString((Object[]) rv) + " (now at " + ofs[1] + ")");
            } else {
                Debug.print(Debug.VERBOSE, "Extracted: " + rv + " (now at " + ofs[1] + ")");
            }
        }
        if (rv instanceof Object[]) {
            //System.out.println("Extracted: " + Arrays.deepToString((Object[]) rv) + " (now at " + ofs[1] + ")");
        } else {
            //System.out.println("Extracted: " + rv + " (now at " + ofs[1] + ")");
        }
        return rv;
    }
public static Value extract(String sig, byte[] buf, byte endian, int[] ofs,Type requestType) throws DBusException {
        if (Debug.debug) {
            Debug.print(Debug.VERBOSE, "extract(" + sig + ",#" + buf.length + ", {" + ofs[0] + "," + ofs[1] + "}");
        }
        //System.out.println("extract(" + sig + ",#" + buf.length + ", {" + ofs[0] + "," + ofs[1] + "}");
        Value rv = Value.create();
        //ValueVector vv = ValueVector.create();
        Map<String,ValueVector> children = rv.children();
        byte[] sigb = sig.getBytes();
        List<Map.Entry<String,Type>> subtypes = new LinkedList<Map.Entry<String,Type>>();
        Set<Map.Entry<String,Type>> hashSubtypes;
        if(requestType != null){
            try{
                java.lang.reflect.Method subTypesMethod = Type.class.getDeclaredMethod("subTypeSet");
                subTypesMethod.setAccessible(true);
                hashSubtypes = (Set<Map.Entry<String,Type>>)subTypesMethod.invoke(requestType);
            } catch(NoSuchMethodException nme){
                throw new DBusException(nme.getMessage());
            } catch (IllegalAccessException iae){
                throw new DBusException(iae.getMessage());
            } catch (InvocationTargetException ite){
                throw new DBusException(ite.getMessage());
            }
            subtypes.addAll(hashSubtypes);
            
            //sort the list.
            Collections.sort(subtypes, new Comparator<Map.Entry<String,Type>>(){
                @Override
                public int compare(Map.Entry<String,Type> o1, Map.Entry<String,Type> o2) {
                    return o1.getKey().compareTo(o2.getKey());
                }
            });
        }
        int counter = 0;
        for (int[] i = ofs; i[0] < sigb.length; i[0]++) {
            if(!subtypes.isEmpty() && subtypes.get(counter) != null){
                Object val = extractone(sigb, buf, endian, i, false,subtypes.get(counter).getValue(),subtypes.get(counter).getKey());
                if(val instanceof Value){
                    ValueVector vv = ValueVector.create();
                    vv.set(0, (Value)val);
                    children.put(subtypes.get(counter).getKey(), vv);
                } else {
                    children.put(subtypes.get(counter).getKey(), (ValueVector)val);
                }
                counter++;
            } else {
                rv = (Value)extractone(sigb, buf, endian, i, false, null,"");
            }
        }
        return rv;
    }
    
    private static Object extractone(byte[] sigb, byte[] buf, byte endian, int[] ofs, boolean contained,jolie.runtime.typing.Type requestType,String name) throws DBusException {
        
        Object rv = null;
        ofs[1] = align(ofs[1], sigb[ofs[0]]);
        List<Map.Entry<String,jolie.runtime.typing.Type>> subtypes;
        Map<String,ValueVector> children;
        switch (sigb[ofs[0]]) {
            case Message.ArgumentType.BYTE:
                rv = Value.create(buf[ofs[1]++]);
                break;
            case Message.ArgumentType.UINT32:
                rv = Value.create(new UInt32(Message.demarshallint(buf, ofs[1], endian, 4)));
                ofs[1] += 4;
                break;
            case Message.ArgumentType.INT32:
                rv = Value.create((int) Message.demarshallint(buf, ofs[1], endian, 4));
                ofs[1] += 4;
                break;
            case Message.ArgumentType.INT16:
                rv = Value.create((short) Message.demarshallint(buf, ofs[1], endian, 2));
                ofs[1] += 2;
                break;
            case Message.ArgumentType.UINT16:
                rv = Value.create(new UInt16((int) Message.demarshallint(buf, ofs[1], endian, 2)));
                ofs[1] += 2;
                break;
            case Message.ArgumentType.INT64:
                rv = Value.create(Message.demarshallint(buf, ofs[1], endian, 8));
                ofs[1] += 8;
                break;
            case Message.ArgumentType.UINT64:
                long top;
                long bottom;
                if (endian == Message.Endian.BIG) {
                    top = Message.demarshallint(buf, ofs[1], endian, 4);
                    ofs[1] += 4;
                    bottom = Message.demarshallint(buf, ofs[1], endian, 4);
                } else {
                    bottom = Message.demarshallint(buf, ofs[1], endian, 4);
                    ofs[1] += 4;
                    top = Message.demarshallint(buf, ofs[1], endian, 4);
                }
                rv = Value.create(new UInt64(top, bottom));
                ofs[1] += 4;
                break;
            case Message.ArgumentType.DOUBLE:
                long l = Message.demarshallint(buf, ofs[1], endian, 8);
                ofs[1] += 8;
                rv = Value.create(Double.longBitsToDouble(l));
                break;
            case Message.ArgumentType.FLOAT:
                ofs[1] += 4;
                //rv = Value.create(Float.intBitsToFloat((int) Message.demarshallint(buf, ofs[1], endian, algn)));
                break;
            case Message.ArgumentType.BOOLEAN:
                int rf = (int) Message.demarshallint(buf, ofs[1], endian, 4);
                ofs[1] += 4;
                rv = (1 == rf) ? Value.create(Boolean.TRUE) : Value.create(Boolean.FALSE);
                break;
            case Message.ArgumentType.ARRAY:
                long size = Message.demarshallint(buf, ofs[1], endian, 4);
                Value val = null;
                ValueVector vv = null;
                if (Debug.debug) {
                    Debug.print(Debug.VERBOSE, "Reading array of size: " + size);
                }
                //System.out.println("Reading array of size: " + size);
                ofs[1] += 4;
                byte algn = (byte) Message.getAlignment(sigb[++ofs[0]]);
                ofs[1] = align(ofs[1], sigb[ofs[0]]);
                int length = (int) (size / algn);
                int counter;
                ValueVector arrayVV;
                if (length > MAX_ARRAY_LENGTH) {
                    throw new MarshallingException("Arrays must not exceed " + MAX_ARRAY_LENGTH);
                    // optimise primatives
                }
                switch (sigb[ofs[0]]) {
                    case Message.ArgumentType.BYTE:
                        byte[] byteArray = new byte[length];
                        System.arraycopy(buf, ofs[1], byteArray, 0, length);
                        vv = ValueVector.create();
                        for(int i = 0; i < byteArray.length;i++){
                            //val.add(Value.create(new ByteArray(new byte[]{byteArray[i]})));
                            vv.set(i,Value.create((byte)byteArray[i]));
                        }
                        ofs[1] += size;
                        rv = vv;
                        break;
                    case Message.ArgumentType.INT16:
                        rv = new short[length];
                        vv = ValueVector.create();
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            //((short[]) rv)[j] = (short) Message.demarshallint(buf, ofs[1], endian, algn);
                            vv.set(j,Value.create((short) Message.demarshallint(buf, ofs[1], endian, algn)));
                        }
                        rv = vv;
                        break;
                    case Message.ArgumentType.INT32:
                        vv = ValueVector.create();
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            vv.set(j,Value.create((int) Message.demarshallint(buf, ofs[1], endian, algn)));
                        }
                        rv = vv;
                        break;
                    case Message.ArgumentType.INT64:
                        vv = ValueVector.create();
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            vv.set(j,Value.create(Message.demarshallint(buf, ofs[1], endian, algn)));
                        }
                        rv = vv;
                        break;
                    case Message.ArgumentType.BOOLEAN:
                        vv = ValueVector.create();
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            vv.set(j,Value.create(1 == Message.demarshallint(buf,ofs[1], endian, algn)));
                        }
                        rv = vv;
                        break;
                    case Message.ArgumentType.FLOAT:
                        vv = ValueVector.create();
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            Float fl = Float.intBitsToFloat((int) Message.demarshallint(buf, ofs[1], endian, algn));
                            vv.set(j,Value.create(fl.doubleValue()));
                        }
                        rv = vv;
                        break;
                    case Message.ArgumentType.DOUBLE:
                        vv = ValueVector.create();
                        for (int j = 0; j < length; j++, ofs[1] += algn) {
                            vv.set(j,Value.create(Double.longBitsToDouble(Message.demarshallint(buf, ofs[1], endian, algn))));
                        }
                        rv = vv;
                        break;
                    case Message.ArgumentType.DICT_ENTRY1:
                        if (0 == size) {
                            // advance the type parser even on 0-size arrays.
                            Vector<java.lang.reflect.Type> temp = new Vector<java.lang.reflect.Type>();
                            byte[] temp2 = new byte[sigb.length - ofs[0]];
                            System.arraycopy(sigb, ofs[0], temp2, 0, temp2.length);
                            String temp3 = new String(temp2);
                            // ofs[0] gets incremented anyway. Leave one character on the stack
                            int temp4 = Marshalling.getJavaType(temp3, temp, 1) - 1;
                            ofs[0] += temp4;
                            if (Debug.debug) {
                                Debug.print(Debug.VERBOSE, "Aligned type: " + temp3 + " " + temp4 + " " + ofs[0]);
                            }
                            //System.out.println("Aligned type: " + temp3 + " " + temp4 + " " + ofs[0]);
                        }
                        int ofssave = ofs[0];
                        long end = ofs[1] + size;
                        
                        subtypes = new LinkedList<Map.Entry<String, jolie.runtime.typing.Type>>();
                        if(requestType != null && ((TypeImpl)requestType).subTypeSet() != null){
                            subtypes.addAll(((TypeImpl)requestType).subTypeSet());
                            //sort the list.
                            Collections.sort(subtypes, new Comparator<Map.Entry<String,jolie.runtime.typing.Type>>(){
                                @Override
                                public int compare(Map.Entry<String, jolie.runtime.typing.Type> o1, Map.Entry<String, jolie.runtime.typing.Type> o2) {
                                    return o1.getKey().compareTo(o2.getKey());
                                }
                            });
                        }
                        counter = 0;
                        arrayVV = ValueVector.create();
                        while (ofs[1] < end) {
                            ofs[0] = ofssave;
                            if(subtypes.size() > 0){
                                arrayVV.set(counter,(Value)extractone(sigb, buf, endian, ofs, true,requestType,name));
                            } else {
                                arrayVV.set(counter, (Value)extractone(sigb, buf, endian, ofs, true,null,null));
                            }
                            counter++;
                        }
                        rv = arrayVV;
                        break;
                    default:
                        if (0 == size) {
                            // advance the type parser even on 0-size arrays.
                            Vector<java.lang.reflect.Type> temp = new Vector<java.lang.reflect.Type>();
                            byte[] temp2 = new byte[sigb.length - ofs[0]];
                            System.arraycopy(sigb, ofs[0], temp2, 0, temp2.length);
                            String temp3 = new String(temp2);
                            // ofs[0] gets incremented anyway. Leave one character on the stack
                            int temp4 = Marshalling.getJavaType(temp3, temp, 1) - 1;
                            ofs[0] += temp4;
                            if (Debug.debug) {
                                Debug.print(Debug.VERBOSE, "Aligned type: " + temp3 + " " + temp4 + " " + ofs[0]);
                            }
                            //System.out.println("Aligned type: " + temp3 + " " + temp4 + " " + ofs[0]);
                        }
                        ofssave = ofs[0];
                        end = ofs[1] + size;

                        Set<Map.Entry<String, jolie.runtime.typing.Type>> subTypeSet;
                        subtypes = new LinkedList<Map.Entry<String, Type>>();
                        
                        try{
                            Method typeImplMethod = Type.class.getDeclaredMethod("subTypeSet");
                            typeImplMethod.setAccessible(true);
                            subTypeSet = (Set<Map.Entry<String, Type>>)typeImplMethod.invoke(requestType);
                        } catch(NoSuchMethodException nsme){
                            throw new DBusException(nsme.toString());
                        } catch(IllegalAccessException iae) {
                            throw new DBusException(iae.toString());
                        } catch(IllegalArgumentException ia){
                            throw new DBusException(ia.toString());
                        } catch(InvocationTargetException ite){ throw new DBusException(ite.toString());}
                        
                        if(requestType != null && subTypeSet != null){
                            subtypes.addAll(subTypeSet);
                            //sort the list.
                            Collections.sort(subtypes, new Comparator<Map.Entry<String,jolie.runtime.typing.Type>>(){
                                @Override
                                public int compare(Map.Entry<String, jolie.runtime.typing.Type> o1, Map.Entry<String, jolie.runtime.typing.Type> o2) {
                                    return o1.getKey().compareTo(o2.getKey());
                                }
                            });
                        }
                        counter = 0;
                        arrayVV = ValueVector.create();
                        
                        while (ofs[1] < end) {
                            ofs[0] = ofssave;
                            if(subtypes.size() > 0){
                                Object extracted = extractone(sigb, buf, endian, ofs, true,requestType,name);
                                if(extracted instanceof Value){
                                    arrayVV.set(counter, (Value)extracted);
                                } else if (extracted instanceof ValueVector) {
                                    Value newVal = Value.create();
                                    newVal.children().put(name, (ValueVector)extracted);
                                    arrayVV.set(counter,newVal);
                                }
                            } else {
                                arrayVV.set(counter, (Value)extractone(sigb, buf, endian, ofs, true,null,null));
                            }
                            counter++;
                        }
                        rv = arrayVV;
                }
                break;
            case Message.ArgumentType.STRUCT1:
                //Vector<Object> contents = new Vector<Object>();
                val = Value.create();
                children = val.children();

                Set<Map.Entry<String, jolie.runtime.typing.Type>> subTypeSet;
                subtypes = new LinkedList<Map.Entry<String, Type>>();
                try {
                    Method typeImplMethod = Type.class.getDeclaredMethod("subTypeSet");
                    typeImplMethod.setAccessible(true);
                    subTypeSet = (Set<Map.Entry<String, Type>>) typeImplMethod.invoke(requestType);
                } catch (NoSuchMethodException nsme) {
                    throw new DBusException(nsme.toString());
                } catch (IllegalAccessException iae) {
                    throw new DBusException(iae.toString());
                } catch (IllegalArgumentException ia) {
                    throw new DBusException(ia.toString());
                } catch (InvocationTargetException ite) {
                    throw new DBusException(ite.toString());
                }
                if (requestType != null && subTypeSet != null) {
                    subtypes.addAll(subTypeSet);
                    //sort the list.
                    Collections.sort(subtypes, new Comparator<Map.Entry<String, jolie.runtime.typing.Type>>() {
                        @Override
                        public int compare(Map.Entry<String, jolie.runtime.typing.Type> o1, Map.Entry<String, jolie.runtime.typing.Type> o2) {
                            return o1.getKey().compareTo(o2.getKey());
                        }
                    });
                }
                counter = 0;
                Object returnVal;
                while (sigb[++ofs[0]] != Message.ArgumentType.STRUCT2) {
                    if(subtypes.size() > 1){
                        returnVal = extractone(sigb, buf, endian, ofs, false,subtypes.get(counter).getValue(),subtypes.get(counter).getKey());
                        if(returnVal instanceof Value){
                            vv = ValueVector.create();
                            vv.set(0, (Value)returnVal);
                            children.put(subtypes.get(counter).getKey(), vv);
                        } else {
                            children.put(subtypes.get(counter).getKey(), (ValueVector)returnVal);
                        }
                    }else if (subtypes.size() == 1){
                        returnVal = extractone(sigb, buf, endian, ofs, false,subtypes.get(0).getValue(),subtypes.get(0).getKey());
                        if(returnVal instanceof Value){
                            vv = ValueVector.create();
                            vv.set(0, (Value)returnVal);
                            children.put(subtypes.get(0).getKey(), vv);
                        } else {
                            children.put(subtypes.get(0).getKey(), (ValueVector)returnVal);
                        }
                    } else {
                        returnVal = extractone(sigb, buf, endian, ofs, false,requestType,name);                        
                        if(returnVal instanceof Value){
                            vv = ValueVector.create();
                            vv.set(0, (Value)returnVal);
                            children.put(name, vv);
                        } else {
                            children.put(name, (ValueVector)returnVal);
                        }
                    }
                    counter++;
                }
                rv = val;
                break;
            case Message.ArgumentType.DICT_ENTRY1:
                if (Debug.debug) {
                    Debug.print(Debug.VERBOSE, "Extracting Dict Entry (" + Hexdump.toAscii(sigb, ofs[0], sigb.length - ofs[0]) + ") from: " + Hexdump.toHex(buf, ofs[1], buf.length - ofs[1]));
                }
                
                /*if (requestType instanceof TypeLink){
                    TypeLink type = (TypeLink)requestType;
                    requestType = (TypeImpl)type.linkedType();
                } else if(requestType instanceof TypeImpl){
                    requestType = (TypeImpl)requestType;
                }*/
                subtypes = new LinkedList<Map.Entry<String, jolie.runtime.typing.Type>>();
                if(requestType != null && ((TypeImpl)requestType).subTypeSet() != null){
                    subtypes.addAll(((TypeImpl)requestType).subTypeSet());
                }
                if(subtypes.size() == 2){
                    ofs[0]++;
                    Object key  = (extractone(sigb, buf, endian, ofs, false,subtypes.get(0).getValue(),subtypes.get(0).getKey()));
                    ofs[0]++;
                    Object tempval = extractone(sigb, buf, endian, ofs, false,subtypes.get(1).getValue(),subtypes.get(1).getKey());
                    Value entry = Value.create();
                    children = entry.children();
                    if(key instanceof Value){
                        ValueVector keyVector = ValueVector.create();
                        keyVector.set(0, (Value)key);
                        children.put("key", keyVector);
                    } else if (key instanceof ValueVector){
                        children.put("key", (ValueVector)key);
                    }
                    if(tempval instanceof Value){
                        ValueVector valueVec = ValueVector.create();
                        valueVec.set(0, (Value)tempval);
                        children.put("value", valueVec);                
                    } else if (tempval instanceof ValueVector){
                        children.put("value", (ValueVector)tempval);
                    }
                    rv = entry;
                }
                ofs[0]++;
                break;
            case Message.ArgumentType.VARIANT:
                int[] newofs = new int[]{0, ofs[1]};
                String sig = (String) extract(Message.ArgumentType.SIGNATURE_STRING, buf, endian, newofs)[0];
                newofs[0] = 0;
                
                //rv = new Variant<Object>(extract(sig, buf, endian, newofs)[0], sig);
                rv = null;
                ofs[1] = newofs[1];
                break;
            case Message.ArgumentType.STRING:
                length = (int) Message.demarshallint(buf, ofs[1], endian, 4);
                ofs[1] += 4;
                try {
                    rv = Value.create(new String(buf, ofs[1], length, "UTF-8"));
                } catch (UnsupportedEncodingException UEe) {
                    if (AbstractConnection.EXCEPTION_DEBUG && Debug.debug) {
                        Debug.print(UEe);
                    }
                    throw new DBusException("System does not support UTF-8 encoding");
                }
                ofs[1] += length + 1;
                break;
            default:
                throw new UnknownTypeCodeException(sigb[ofs[0]]);
        }
        return rv;
    }
    /**
     * Align a counter to the given type.
     *
     * @param current The current counter.
     * @param type The type to align to.
     * @return The new, aligned, counter.
     */
    public static int align(int current, byte type) {
        if (Debug.debug) {
            Debug.print(Debug.VERBOSE, "aligning to " + (char) type);
        }
        //System.out.println("aligning to " + (char) type);
        int a = Message.getAlignment(type);
        if (0 == (current % a)) {
            return current;
        }
        return current + (a - (current % a));
    }
}
