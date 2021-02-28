package inside;

import java.text.MessageFormat;
import java.util.*;

import static inside.NeutralPlugin.config;

// static?
public class Bundle{

    public String get(String key, Locale locale){
        try{
            ResourceBundle bundle = ResourceBundle.getBundle("bundles.bundle", locale);
            return bundle.containsKey(key) ? bundle.getString(key) : "???" + key + "???";
        }catch(MissingResourceException t){
            // may be a fall to infinite loop
            return get(key, config.locale);
        }
    }

    public String get(String key){
        return get(key, config.locale);
    }

    public String format(String key, Locale locale, Object... values){
        return MessageFormat.format(get(key, locale), values);
    }

    public String format(String key, Object... values){
        return format(key, config.locale, values);
    }
}
