package org.codesearch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;

public class Units {
    public static class Unit {
        protected String file;
        protected int line;
        protected int position;
        protected JSONObject json;
        protected List<String> keys;

        protected static Set<String> infoKeys = new HashSet<>();

        public Unit(String file, int line, int position, UnitContent content) {
            this.file = file;
            this.line = line;
            this.position = position;
            this.json = content.getJson();
            this.keys = content.getKeys();
            for (String infoKey: content.getInfoKeys()) {
                infoKeys.add(infoKey);
            }
        }

        public Unit(String file, int line, int position, JSONObject json, List<String> keys) {
            this.file = file;
            this.line = line;
            this.position = position;
            this.json = json;
            this.keys = keys;
        }

        public JSONObject getJSON() {return json;}
        public List<String> getKeys() {return keys;}
        public String getFile() {return file;}
        public int getLine() {return line;}
        public int getPosition() {return position;}
        public Set<String> getInfoKeys() {return infoKeys;}

        @Override
        public String toString() {
            return String.format("%s:%d:%d", file, line, position);
        }
    }

    public static interface UnitContent {
        public JSONObject getJson();
        public List<String> getKeys();
        public String getName();
        public List<String> getInfoKeys();
    }    
}
