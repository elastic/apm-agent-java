
package co.elastic.apm.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.List;


/**
 * Process
 * <p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "pid",
    "ppid",
    "title",
    "argv"
})
// TODO: make immutable
public class Process {

    /**
     * Process ID of the service
     * (Required)
     */
    @JsonProperty("pid")
    @JsonPropertyDescription("Process ID of the service")
    private long pid;
    /**
     * Parent process ID of the service
     */
    @JsonProperty("ppid")
    @JsonPropertyDescription("Parent process ID of the service")
    private long ppid;
    @JsonProperty("title")
    private String title;
    /**
     * Command line arguments used to start this process
     */
    @JsonProperty("argv")
    @JsonPropertyDescription("Command line arguments used to start this process")
    private List<String> argv = new ArrayList<String>();

    /**
     * Process ID of the service
     * (Required)
     */
    @JsonProperty("pid")
    public long getPid() {
        return pid;
    }

    /**
     * Process ID of the service
     * (Required)
     */
    @JsonProperty("pid")
    public void setPid(long pid) {
        this.pid = pid;
    }

    public Process withPid(long pid) {
        this.pid = pid;
        return this;
    }

    /**
     * Parent process ID of the service
     */
    @JsonProperty("ppid")
    public long getPpid() {
        return ppid;
    }

    /**
     * Parent process ID of the service
     */
    @JsonProperty("ppid")
    public void setPpid(long ppid) {
        this.ppid = ppid;
    }

    public Process withPpid(long ppid) {
        this.ppid = ppid;
        return this;
    }

    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    @JsonProperty("title")
    public void setTitle(String title) {
        this.title = title;
    }

    public Process withTitle(String title) {
        this.title = title;
        return this;
    }

    /**
     * Command line arguments used to start this process
     */
    @JsonProperty("argv")
    public List<String> getArgv() {
        return argv;
    }

    /**
     * Command line arguments used to start this process
     */
    @JsonProperty("argv")
    public void setArgv(List<String> argv) {
        this.argv = argv;
    }

    public Process withArgv(List<String> argv) {
        this.argv = argv;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("pid", pid).append("ppid", ppid).append("title", title).append("argv", argv).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(pid).append(title).append(argv).append(ppid).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Process) == false) {
            return false;
        }
        Process rhs = ((Process) other);
        return new EqualsBuilder().append(pid, rhs.pid).append(title, rhs.title).append(argv, rhs.argv).append(ppid, rhs.ppid).isEquals();
    }

}
