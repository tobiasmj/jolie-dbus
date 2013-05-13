/***************************************************************************
 * Copyright (C) 2013 by Tobias Mandrup Johansen <tobias.mandrup@gmail.com>*
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

package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.types.UInt64;
import jolie.lang.parse.context.ParsingContext;


public class ConstantUInteger64Expression extends OLSyntaxNode
{
	private final UInt64 value;

	public ConstantUInteger64Expression( ParsingContext context, UInt64 value )
	{
		super( context );
		this.value = value;
	}
	
	public UInt64 value()
	{
		return value;
	}
	
	public void accept( OLVisitor visitor )
	{
		visitor.visit( this );
	}
}
