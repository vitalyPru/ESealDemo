package ru.spb.gamma.esealdemo;

import java.util.ArrayList;
import java.util.List;

public class LogContent {
    public static final List<LogContent.LogItem> ITEMS = new ArrayList<LogContent.LogItem>();

    /**
     * A map of sample (dummy) items, by ID.
     */
//    public static final Map<String, LogContent.LogItem> ITEM_MAP = new HashMap<String, LogContent.LogItem>();

//    private static final int COUNT = 25;

//    static {
//        // Add some sample items.
//        for (int i = 1; i <= COUNT; i++) {
//            addItem(createDummyItem(i));
//        }
//    }

    private static void addItem(LogContent.LogItem item) {
        ITEMS.add(item);
//        ITEM_MAP.put(item.id, item);
    }

//    private static LogContent.LogItem createLogItem(int position) {
//        return new DummyContent.DummyItem(String.valueOf(position), "Item " + position, makeDetails(position));
//    }

//    private static String makeDetails(int position) {
//        StringBuilder builder = new StringBuilder();
//        builder.append("Details about Item: ").append(position);
//        for (int i = 0; i < position; i++) {
//            builder.append("\nMore details information here.");
//        }
//        return builder.toString();
//    }

    /**
     * A dummy item representing a piece of content.
     */
    public static class LogItem {
//        public final String id;
        public final String time;
        public final String data;
        public final int type;

        public LogItem( String time, String data, int type) {
            this.time = time;
            this.data = data;
            this.type = type;
        }

        @Override
        public String toString() {
            return time+":"+data;
        }
    }
}
