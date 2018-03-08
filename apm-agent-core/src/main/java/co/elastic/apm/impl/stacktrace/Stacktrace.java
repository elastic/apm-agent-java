
package co.elastic.apm.impl.stacktrace;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Stacktrace
 * <p>
 * A stacktrace frame, contains various bits (most optional) describing the context of the frame
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "abs_path",
    "colno",
    "context_line",
    "filename",
    "function",
    "library_frame",
    "lineno",
    "module",
    "post_context",
    "pre_context",
    "vars"
})
public class Stacktrace implements Recyclable {

    /**
     * The absolute path of the file involved in the stack frame
     */
    @JsonProperty("abs_path")
    @JsonPropertyDescription("The absolute path of the file involved in the stack frame")
    private String absPath;
    /**
     * The relative filename of the code involved in the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    @JsonProperty("filename")
    @JsonPropertyDescription("The relative filename of the code involved in the stack frame, used e.g. to do error checksumming")
    private String filename;
    /**
     * The function involved in the stack frame
     */
    @JsonProperty("function")
    @JsonPropertyDescription("The function involved in the stack frame")
    private String function;
    /**
     * A boolean, indicating if this frame is from a library or user code
     */
    @JsonProperty("library_frame")
    @JsonPropertyDescription("A boolean, indicating if this frame is from a library or user code")
    private boolean libraryFrame;
    /**
     * The line number of code part of the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    @JsonProperty("lineno")
    @JsonPropertyDescription("The line number of code part of the stack frame, used e.g. to do error checksumming")
    private long lineno;
    /**
     * The module to which frame belongs to
     */
    @JsonProperty("module")
    @JsonPropertyDescription("The module to which frame belongs to")
    private String module;

    /**
     * The absolute path of the file involved in the stack frame
     */
    @JsonProperty("abs_path")
    public String getAbsPath() {
        return absPath;
    }

    /**
     * The absolute path of the file involved in the stack frame
     */
    @JsonProperty("abs_path")
    public void setAbsPath(String absPath) {
        this.absPath = absPath;
    }

    public Stacktrace withAbsPath(String absPath) {
        this.absPath = absPath;
        return this;
    }

    /**
     * The relative filename of the code involved in the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    @JsonProperty("filename")
    public String getFilename() {
        return filename;
    }

    /**
     * The relative filename of the code involved in the stack frame, used e.g. to do error checksumming
     * (Required)
     */
    @JsonProperty("filename")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Stacktrace withFilename(String filename) {
        this.filename = filename;
        return this;
    }

    /**
     * The function involved in the stack frame
     */
    @JsonProperty("function")
    public String getFunction() {
        return function;
    }

    /**
     * The function involved in the stack frame
     */
    @JsonProperty("function")
    public void setFunction(String function) {
        this.function = function;
    }

    public Stacktrace withFunction(String function) {
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
    @JsonProperty("library_frame")
    public void setLibraryFrame(boolean libraryFrame) {
        this.libraryFrame = libraryFrame;
    }

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
    @JsonProperty("lineno")
    public void setLineno(long lineno) {
        this.lineno = lineno;
    }

    public Stacktrace withLineno(long lineno) {
        this.lineno = lineno;
        return this;
    }

    /**
     * The module to which frame belongs to
     */
    @JsonProperty("module")
    public String getModule() {
        return module;
    }

    /**
     * The module to which frame belongs to
     */
    @JsonProperty("module")
    public void setModule(String module) {
        this.module = module;
    }

    public Stacktrace withModule(String module) {
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
