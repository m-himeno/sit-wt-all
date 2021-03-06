package io.sitoolkit.wt.infra.resource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

public class MessageManager {

  private static final Logger LOG = LoggerFactory.getLogger(MessageManager.class);

  private static ResourceBundle resource;

  private static Map<String, String> resourceMap;

  private static synchronized ResourceBundle getResource() {
    if (resource == null) {
      String baseName = MessageManager.class.getPackage().getName().replace(".", "/") + "/message";
      LOG.info("message resource is initialized with locale {}", Locale.getDefault());
      resource = ResourceBundle.getBundle(baseName);
    }
    return resource;
  }

  public static String getMessage(String key) {
    try {
      return getResource().getString(key);
    } catch (MissingResourceException e) {
      LOG.warn("{}, locale {}", e.getMessage(), getResource().getLocale());
    }
    return "!! messing resource !!";

  }

  public static String getMessage(String key, Object... params) {
    try {
      return MessageFormatter.arrayFormat(getResource().getString(key), params).getMessage();
    } catch (MissingResourceException e) {
      LOG.warn("{}, locale {}", e.getMessage(), getResource().getLocale());
    }
    return "!! messing resource !!";

  }

  public synchronized static Map<String, String> getResourceAsMap() {
    if (resourceMap != null) {
      return resourceMap;
    }

    Map<String, String> map = new HashMap<>();
    ResourceBundle resourceBundle = getResource();
    for (String key : Collections.list(resourceBundle.getKeys())) {
      map.put(key, resourceBundle.getString(key));
    }
    resourceMap = Collections.unmodifiableMap(map);

    return resourceMap;
  }

}
