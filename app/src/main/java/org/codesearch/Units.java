package org.codesearch;

import java.util.List;

import org.json.JSONObject;

public class Units {
    public static class Unit {
        protected String file;
        protected int line;
        protected JSONObject json;
        protected List<String> keys;

        public Unit(String file, int line, UnitContent content) {
            this.file = file;
            this.line = line;
            this.json = content.getJson();
            this.keys = content.getKeys();
        }

        public Unit(String file, int line, JSONObject json, List<String> keys) {
            this.file = file;
            this.line = line;
            this.json = json;
            this.keys = keys;
        }

        public JSONObject getJSON() {return json;}
        public List<String> getKeys() {return keys;}
        public String getFile() {return file;}
        public int getLine() {return line;}
    }

    public static interface UnitContent {
        public JSONObject getJson();
        public List<String> getKeys();
        public String getName();
    }    
}
