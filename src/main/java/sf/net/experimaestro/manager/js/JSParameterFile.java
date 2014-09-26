package sf.net.experimaestro.manager.js;

import java.io.UnsupportedEncodingException;

/**
 * A parameter file
 *
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 * @date 24/1/13
 */
public class JSParameterFile extends JSBaseObject {
    private String key;
    private byte[] value;


    @JSFunction
    public JSParameterFile(String key, byte[] value) {
        this.setKey(key);
        this.setValue(value);
    }

    @JSFunction
    public JSParameterFile(String key, JSBaseObject object) {
        this(key, object.getBytes());
    }

    @JSFunction
    public JSParameterFile(String key, String value, String encoding) throws UnsupportedEncodingException {
        this(key, value.getBytes(encoding));
    }

    @JSFunction
    public JSParameterFile(String key, String value) throws UnsupportedEncodingException {
        this(key, value, "UTF-8");
    }


    @Override
    public String toString() {
        return getKey();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }
}
