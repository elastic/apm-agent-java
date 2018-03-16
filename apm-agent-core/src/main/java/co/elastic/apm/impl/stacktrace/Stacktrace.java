
package co.elastic.apm.impl.stacktrace;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.annotation.Nullable;


/**
 * Stacktrace
 * <p>
 * A stacktrace frame, contains various bits (most optional) describing the context of the frame
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Stacktrace implements Recyclable {

    /**
     * The absolute path of the file involved in the stack frame
     */
    @Nullable
    @JsonProperty("abs_path")
    private String absPath;
    /**
     * The relative filename of the code involved in the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    @Nullable
    @JsonProperty("filename")
    private String filename;
    /**
     * The function involved in the stack frame
     */
    @Nullable
    @JsonProperty("function")
    private String function;
    /**
     * A boolean, indicating if this frame is from a library or user code
     */
    @JsonProperty("library_frame")
    private boolean libraryFrame;
    /**
     * The line number of code part of the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    @JsonProperty("lineno")
    private long lineno;
    /**
     * The module to which frame belongs to
     */
    @Nullable
    @JsonProperty("module")
    private String module;

    /**
     * The absolute path of the file involved in the stack frame
     */
    @Nullable
    @JsonProperty("abs_path")
    public String getAbsPath() {
        return absPath;
    }

    /**
     * The absolute path of the file involved in the stack frame
     */
    public Stacktrace withAbsPath(@Nullable String absPath) {
        this.absPath = absPath;
        return this;
    }

    /**
     * The relative filename of the code involved in the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    @Nullable
    @JsonProperty("filename")
    public String getFilename() {
        return filename;
    }

    /**
     * The relative filename of the code involved in the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    public Stacktrace withFilename(@Nullable String filename) {
        this.filename = filename;
        return this;
    }

    /**
     * The function involved in the stack frame
     */
    @Nullable
    @JsonProperty("function")
    public String getFunction() {
        return function;
    }

    /**
     * The function involved in the stack frame
     */
    public Stacktrace withFunction(@Nullable String function) {
        this.function = function;
        return this;
    }

    /**
     * A boolean, indicating if this frame is from a library or user code
     */
    @JsonProperty("library_frame")
    public boolean isLibraryFrame() {
        return libraryFrame;
    }

    /**
     * A boolean, indicating if this frame is from a library or user code
     */
    public Stacktrace withLibraryFrame(boolean libraryFrame) {
        this.libraryFrame = libraryFrame;
        return this;
    }

    /**
     * The line number of code part of the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    @JsonProperty("lineno")
    public long getLineno() {
        return lineno;
    }

    /**
     * The line number of code part of the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    public Stacktrace withLineno(long lineno) {
        this.lineno = lineno;
        return this;
    }

    /**
     * The module to which frame belongs to
     */
    @Nullable
    @JsonProperty("module")
    public String getModule() {
        return module;
    }

    /**
     * The module to which frame belongs to
     */
    public Stacktrace withModule(@Nullable String module) {
        this.module = module;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("absPath", absPath)
            .append("filename", filename)
            .append("function", function)
            .append("libraryFrame", libraryFrame)
            .append("lineno", lineno)
            .append("module", module).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(filename)
            .append(lineno)
            .append(absPath)
            .append(function)
            .append(module)
            .append(libraryFrame).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Stacktrace) == false) {
            return false;
        }
        Stacktrace rhs = ((Stacktrace) other);
        return new EqualsBuilder()
            .append(filename, rhs.filename)
            .append(lineno, rhs.lineno)
            .append(absPath, rhs.absPath)
            .append(function, rhs.function)
            .append(module, rhs.module)
            .append(libraryFrame, rhs.libraryFrame).isEquals();
    }

    @Override
    public void resetState() {
        absPath = null;
        filename = null;
        function = null;
        libraryFrame = false;
        lineno = 0;
        module = null;
    }
}
