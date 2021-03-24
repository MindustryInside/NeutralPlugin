package inside;

import arc.struct.ObjectMap;

import java.text.MessageFormat;
import java.util.*;

import static inside.NeutralPlugin.config;

public class Bundle{

    private static final ObjectMap<Locale, ResourceBundle> bundles = new ObjectMap<>();

    private static final ObjectMap<Locale, MessageFormat> formats = new ObjectMap<>();

    private Bundle(){}

    public static String get(String key, Locale locale){
        try{
            ResourceBundle bundle = getOrLoad(locale);
            return bundle != null && bundle.containsKey(key) ? bundle.getString(key) : "???" + key + "???";
        }catch(MissingResourceException t){
            return key;
        }
    }

    public static boolean has(String key){
        return getOrLoad(config.locale).containsKey(key);
    }

    public static String get(String key){
        return get(key, config.locale);
    }

    public static String format(String key, Locale locale, Object... values){
        String pattern = get(key, locale);
        MessageFormat format = formats.get(locale);
        if(!Config.supportedLocales.contains(locale)){
            format = formats.get(config.locale, () -> new MessageFormat(pattern, config.locale));
            format.applyPattern(pattern);
        }else if(format == null){
            format = new MessageFormat(pattern, locale);
            formats.put(locale, format);
        }else{
            format.applyPattern(pattern);
        }
        return format.format(values);
    }

    public static String format(String key, Object... values){
        return format(key, config.locale, values);
    }

    private static ResourceBundle getOrLoad(Locale locale){
        ResourceBundle bundle = bundles.get(locale);
        if(bundle == null && Config.supportedLocales.contains(locale)){
            bundles.put(locale, bundle = ResourceBundle.getBundle("bundles.bundle", locale));
        }else{
            bundle = bundles.get(config.locale);
        }
        return bundle;
    }
}
