/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/
package jolie.net.dbus;

import java.util.Vector;
import org.freedesktop.dbus.exceptions.DBusException;

public class MethodReturn extends Message
{
   MethodReturn() { }
   public MethodReturn(String dest, long replyserial,byte endian, String sig, Object... args) throws DBusException
   {
      this(null, dest, endian,replyserial, sig, args);
   }
   public MethodReturn(String source, String dest,byte endian, long replyserial, String sig, Object... args) throws DBusException
   {
      super(endian, Message.MessageType.METHOD_RETURN, (byte) 0);

      headers.put(Message.HeaderField.REPLY_SERIAL,replyserial);

      Vector<Object> hargs = new Vector<Object>();
      hargs.add(new Object[] { Message.HeaderField.REPLY_SERIAL, new Object[] { ArgumentType.UINT32_STRING, replyserial } });
      
      if (null != source) {
         headers.put(Message.HeaderField.SENDER,source);
         hargs.add(new Object[] { Message.HeaderField.SENDER, new Object[] { ArgumentType.STRING_STRING, source } });
      }
 
      if (null != dest) {
         headers.put(Message.HeaderField.DESTINATION,dest);
         hargs.add(new Object[] { Message.HeaderField.DESTINATION, new Object[] { ArgumentType.STRING_STRING, dest } });
      }

      if (null != sig) {
         hargs.add(new Object[] { Message.HeaderField.SIGNATURE, new Object[] { ArgumentType.SIGNATURE_STRING, sig } });
         headers.put(Message.HeaderField.SIGNATURE,sig);
         setArgs(args);
      }

      byte[] blen = new byte[4];
      appendBytes(blen);
      append("ua(yv)", serial, hargs.toArray());
      pad((byte)8);

      long c = bytecounter;
      if (null != sig) append(sig, args);
      marshallint(bytecounter-c, blen, 0, 4);
   }
   public MethodReturn(MethodCall mc,byte endian, String sig, Object... args) throws DBusException
   {
      this(null, mc, endian,sig, args);
   }
   public MethodReturn(String source, MethodCall mc, byte endian,String sig, Object... args) throws DBusException
   {
      this(source, mc.getSource(),endian, mc.getSerial(), sig, args);
      this.call = mc;
   }
   MethodCall call;
   public MethodCall getCall() { return call; }
   protected void setCall(MethodCall call) { this.call = call; }
}
