package io.scanbot.sdk.plugin.cordova.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


public class JsonArgs {

    private final Map<String, Object> argsMap = new HashMap<String, Object>();

    public JsonArgs put(final String key, final String value) {
        this.argsMap.put(key, value);
        return this;
    }

    public JsonArgs put(final String key, final int value) {
        this.argsMap.put(key, value);
        return this;
    }

    public JsonArgs put(final String key, final double value) {
        this.argsMap.put(key, value);
        return this;
    }

    public JsonArgs put(final String key, final float value) {
        this.argsMap.put(key, value);
        return this;
    }

    public JsonArgs put(final String key, final boolean value) {
        this.argsMap.put(key, value);
        return this;
    }

    public JsonArgs put(final String key, final JSONArray value) {
        this.argsMap.put(key, value);
        return this;
    }

    public JsonArgs put(final String key, final JsonArgs value) {
        this.argsMap.put(key, value.jsonObj());
        return this;
    }

    public JSONObject jsonObj() {
        return new JSONObject(this.argsMap);
    }
}
