/***************************************************************************
 *   Copyright (C) by Fabrizio Montesi                                     *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/
package jolie.net;

import jolie.net.ports.OutputPort;
import cx.ath.matthew.unix.UnixSocket;
import cx.ath.matthew.unix.UnixSocketAddress;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import jolie.net.ext.CommChannelFactory;
import jolie.net.protocols.CommProtocol;
import jolie.runtime.AndJarDeps;

@AndJarDeps({"unix.jar"})
public class LocalSocketCommChannelFactory extends CommChannelFactory
{
	public LocalSocketCommChannelFactory( CommCore commCore )
	{
		super( commCore );
	}

	public CommChannel createChannel( URI location, OutputPort port )
		throws IOException
	{
		UnixSocket socket = new UnixSocket( new UnixSocketAddress( location.getPath(), location.getScheme().equals( "abs" ) ) );
		CommChannel ret = null;
		try {
			ret = new LocalSocketCommChannel( socket, location, port.getProtocol() );
                        if(port.messageBus()){
                            ret.setParentOutputPort(port);
                            /*Using reflection to access method, without it will throw an IlligalAccessException,
                            * This happens when access to a protected method, in a class that in the same packet, 
                            * but loaded with a different class loader.
                            * 
                            * JVM specification 5.3
                            * At run time, a class or interface is determined not by its name alone, but by a pair:
                            * its binary name (§4.2.1) and its defining class loader. Each such class or interface
                            * belongs to a single run-time package. The run-time package of a class or interface
                            * is determined by the package name and defining class loader of the class or interface.
                            * 
                            * JVM specification 5.4.4
                            * A field or method R is accessible to a class or interface D if and only if any of the
                            * following conditions are true:
                            * ...
                            * R is either protected or has default access (that is, neither public nor protected
                            * nor private), and is declared by a class in the same run-time package as D.
                            */ 
                            try{
                                Method protocolMethod = StreamingCommChannel.class.getDeclaredMethod("protocol");
                                protocolMethod.setAccessible(true);
                        
                                CommProtocol protocol = (CommProtocol)protocolMethod.invoke(ret);
                                protocol.setup(socket.getInputStream(), socket.getOutputStream());
                            } catch (NoSuchMethodException ne){
                                ne.printStackTrace();
                            } catch (IllegalAccessException ie){
                                ie.printStackTrace();
                            } catch (InvocationTargetException ite){
                                ite.printStackTrace();
                            }
                        }
		} catch( URISyntaxException e ) {
			throw new IOException( e );
		}
		return ret;
	}
}
