/*  tigase-muc
 *  Copyright (C) 2007 by Bartosz M. Małkowski
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 *  $Id$
 */
package org.tigase.form;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-05-27 11:41:02
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class Form {

    private List<Field> fields = new LinkedList<Field>();

    private Map<String, Field> fieldsByVar = new HashMap<String, Field>();

    private String instruction;

    private String title;

    private String type;

    public Form(Element form) {
        this.type = form.getAttribute("type");
        List<Element> children = form.getChildren();
        if (children != null) {
            for (Element sub : children) {
                if ("title".equals(sub.getName())) {
                    this.title = sub.getCData();
                } else if ("instructions".equals(sub.getName())) {
                    this.instruction = sub.getCData();
                } else if ("field".equals(sub.getName())) {
                    Field field = new Field(sub);
                    this.fields.add(field);
                    this.fieldsByVar.put(field.getVar(), field);
                }
            }
        }
    }

    public Form(String type, String title, String instruction) {
        this.type = type;
        this.title = title;
        this.instruction = instruction;
    }

    public void addField(Field field) {
        this.fields.add(field);
    }

    public Field get(String var) {
        return this.fieldsByVar.get(var);
    }

    public Boolean getAsBoolean(String var) {
        Field f = get(var);
        if (f != null) {
            String v = f.getValue();
            if (v == null) {
                return null;
            } else if ("1".equals(v)) {
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
            }
        } else
            return null;
    }

    /**
     * @param var
     * @return
     */
    public Integer getAsInteger(String var) {
        Field f = get(var);
        if (f != null) {
            String v = f.getValue();
            return Integer.parseInt(v);
        } else
            return null;
    }

    public String getAsString(String var) {
        Field f = get(var);
        if (f != null) {
            String v = f.getValue();
            return v;
        } else
            return null;
    }

    public String[] getAsStrings(String var) {
        Field f = get(var);
        if (f != null) {
            String[] v = f.getValues();
            return v;
        } else
            return null;
    }

    public Element getElement() {
        Element form = new Element("x");
        form.setAttribute("xmlns", "jabber:x:data");
        form.setAttribute("type", type);

        if (this.title != null) {
            form.addChild(new Element("title", this.title));
        }
        if (this.instruction != null) {
            form.addChild(new Element("instructions", this.instruction));
        }
        for (Field field : this.fields) {
            form.addChild(field.getElement());
        }
        return form;
    }

    /**
     * @return Returns the instruction.
     */
    public String getInstruction() {
        return instruction;
    }

    /**
     * @return Returns the title.
     */
    public String getTitle() {
        return title;
    }

    /**
     * @return Returns the type.
     */
    public String getType() {
        return type;
    }

    public boolean is(String var) {
        return this.fieldsByVar.containsKey(var);
    }

    /**
     * @param instruction
     *            The instruction to set.
     */
    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    /**
     * @param title
     *            The title to set.
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * @param type
     *            The type to set.
     */
    public void setType(String type) {
        this.type = type;
    }
}
