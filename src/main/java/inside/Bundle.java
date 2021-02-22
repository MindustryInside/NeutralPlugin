package inside;

import java.text.MessageFormat;
import java.util.*;

import static inside.NeutralPlugin.config;

public class Bundle{

    public String get(String key, Locale locale){
        try{
            return ResourceBundle.getBundle("bundles.bundle", locale).getString(key);
        }catch(Throwable t){
            if(t instanceof MissingResourceException){
                return get(key, config.locale);
            }
            return "???" + key + "???";
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
