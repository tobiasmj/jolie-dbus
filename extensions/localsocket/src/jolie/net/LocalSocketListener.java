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

import cx.ath.matthew.unix.UnixServerSocket;
import cx.ath.matthew.unix.UnixSocket;
import cx.ath.matthew.unix.UnixSocketAddress;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.ClosedByInterruptException;

import jolie.Interpreter;
import jolie.net.ext.CommProtocolFactory;
import jolie.net.ports.InputPort;
import jolie.net.protocols.CommProtocol;

public class LocalSocketListener extends CommListener
{
	final private UnixServerSocket serverSocket;
        final private UnixSocket clientSocket;
	final private UnixSocketAddress socketAddress;
	public LocalSocketListener(
				Interpreter interpreter,
				CommProtocolFactory protocolFactory,
				InputPort inputPort
			)
		throws IOException
	{
		super( interpreter, protocolFactory, inputPort );

		socketAddress = new UnixSocketAddress( inputPort.location().getPath(), inputPort.location().getScheme().equals( "abs" ) );
		if(inputPort().messageBus()){
                    clientSocket = new UnixSocket(socketAddress);
                    serverSocket = null;
                } else {
                    serverSocket = new UnixServerSocket( socketAddress );                
                    clientSocket = null;
                }
                
	}
	
	@Override
	public void shutdown()
	{
		if ( !socketAddress.isAbstract() ) {
			new File( socketAddress.getPath() ).delete();
		}
	}

	@Override
	public void run()
	{
		try {
			UnixSocket socket;
			StreamingCommChannel channel;
                        if(serverSocket != null){
                            while ( (socket = serverSocket.accept()) != null ) {
                            	channel = new LocalSocketCommChannel(
                            					socket,
								inputPort().location(),
								createProtocol()
							);
				channel.setParentInputPort( inputPort() );
				interpreter().commCore().scheduleReceive( channel, inputPort() );
				channel = null; // Dispose for garbage collection
                            }
                            serverSocket.close();
                        } else if(clientSocket != null){
                            socket = clientSocket;
                            channel = new LocalSocketCommChannel(socket, inputPort().location(), createProtocol());
                            channel.setParentInputPort(inputPort());
                        
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
                            Method protocolMethod = StreamingCommChannel.class.getDeclaredMethod("protocol");
                            protocolMethod.setAccessible(true);
                        
                            CommProtocol protocol = (CommProtocol)protocolMethod.invoke(channel);
                            protocol.setup(socket.getInputStream(), socket.getOutputStream());
                            interpreter().commCore().scheduleReceive(channel, inputPort());
                            channel = null;
                       }
		} catch( ClosedByInterruptException ce ) {
			try {
				serverSocket.close();
			} catch( IOException e ) {
				e.printStackTrace();
			}
		} catch( IOException e ) {
			e.printStackTrace();
		} catch (NoSuchMethodException ne){
                    ne.printStackTrace();
                } catch (IllegalAccessException ie){
                    ie.printStackTrace();
                } catch (InvocationTargetException ite){
                    ite.printStackTrace();
                }
	}
}
