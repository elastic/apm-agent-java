package co.elastic.apm.util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class MultiValueMap<K, V> extends HashMap<K, List<V>> {

    public void add(K key, V value) {
        if (containsKey(key)) {
            get(key).add(value);
        } else {
            // using linked list as most of the time there will only be one value
            // use cases are HTTP headers and URL parameters
            LinkedList<V> valueList = new LinkedList<>();
            valueList.add(value);
            put(key, valueList);
        }
    }
}
