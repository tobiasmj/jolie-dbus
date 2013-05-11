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
import org.freedesktop.dbus.exceptions.MessageFormatException;

/**
 * Error messages which can be sent over the bus.
 */
public class Error extends Message
{
   Error() { }
   public Error(String dest, String errorName, long replyserial, String sig, Object... args) throws DBusException
   {
      this(null, dest, errorName, replyserial, sig, args);
   }
   public Error(String source, String dest, String errorName, long replyserial, String sig, Object... args) throws DBusException
   {
      super(Message.Endian.BIG, Message.MessageType.ERROR, (byte) 0);

      if (null == errorName)
         throw new MessageFormatException("Must specify error name to Errors.");
      headers.put(Message.HeaderField.REPLY_SERIAL,replyserial);
      headers.put(Message.HeaderField.ERROR_NAME,errorName);
      
      Vector<Object> hargs = new Vector<Object>();
      hargs.add(new Object[] { Message.HeaderField.ERROR_NAME, new Object[] { ArgumentType.STRING_STRING, errorName } });
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
}
