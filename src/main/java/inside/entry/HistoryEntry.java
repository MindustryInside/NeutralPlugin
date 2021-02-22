package inside.entry;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public interface HistoryEntry{

    String getMessage(Locale locale);

    long getLastAccessTime(TimeUnit unit);
}
