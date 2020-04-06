package co.elastic.apm.log.shipper;

import java.io.File;
import java.io.IOException;

public interface FileChangeListener {
    void onLineAvailable(File file, byte[] line, int offset, int length, boolean eol) throws IOException;
}
