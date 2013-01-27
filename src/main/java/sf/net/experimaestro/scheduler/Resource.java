/*
 * This file is part of experimaestro.
 * Copyright (c) 2012 B. Piwowarski <benjamin@bpiwowar.net>
 *
 * experimaestro is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * experimaestro is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with experimaestro.  If not, see <http://www.gnu.org/licenses/>.
 */

package sf.net.experimaestro.scheduler;

import bpiwowar.argparser.utils.ReadLineIterator;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.Relationship;
import com.sleepycat.persist.model.SecondaryKey;
import org.apache.commons.vfs2.FileObject;
import sf.net.experimaestro.connectors.Connector;
import sf.net.experimaestro.connectors.SingleHostConnector;
import sf.net.experimaestro.exceptions.ExperimaestroCannotOverwrite;
import sf.net.experimaestro.locks.Lock;
import sf.net.experimaestro.locks.LockType;
import sf.net.experimaestro.locks.StatusLock;
import sf.net.experimaestro.locks.UnlockableException;
import sf.net.experimaestro.utils.log.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.TreeMap;

import static java.lang.String.format;

/**
 * The most general type of object manipulated by the server (can be a server, a
 * task, or a data)
 *
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 */

/**
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 */
@Entity
public abstract class Resource implements Comparable<Resource>, Cleaneable {
    /**
     * Keeps track of the changes when updating a class
     */
    static public class Changes {
        public static final Changes STATE = new Changes(true);

        boolean state = false;
        TreeMap<ResourceLocator, Dependency> dependencies = new TreeMap<>();

        public Changes() {
        }

        public Changes(boolean state) {
            this.state = state;
        }

        /**
         * Returns true if something has changed
         */
        public boolean changed() {
            return state || !dependencies.isEmpty();
        }
    }

    final static private Logger LOGGER = Logger.getLogger();

    /**
     * Extension for the lock file
     */
    public static final String LOCK_EXTENSION = ".lock";

    /**
     * Extension for the file that describes the status of the resource
     */
    public static final String STATUS_EXTENSION = ".status";

    /**
     * Extension used to mark a produced resource
     */
    public static final String DONE_EXTENSION = ".done";

    /**
     * Extension for the file containing the return code
     */
    public static final String CODE_EXTENSION = ".code";

    /**
     * Extension for the file containing the script to run
     */
    public static final String RUN_EXTENSION = ".run";

    /**
     * Extension for the standard output of a job
     */
    public static final String OUT_EXTENSION = ".out";

    /**
     * Extension for the standard error of a job
     */
    public static final String ERR_EXTENSION = ".err";

    /**
     * Extension for the standard input of a job
     */
    public static final String INPUT_EXTENSION = ".input";

    /**
     * Secondary key for "keys"
     */
    public static final String GROUP_KEY_NAME = "group";

    /**
     * Secondary key for "state"
     */
    public static final String STATE_KEY_NAME = "state";

    /**
     * The locator for this resource
     */
    @PrimaryKey
    ResourceLocator locator;

    /**
     * Group this resource belongs to. Note that name are 0 separated for sorting reasons
     */
    @SecondaryKey(name = GROUP_KEY_NAME, relate = Relationship.MANY_TO_ONE)
    private String group = null;

    /**
     * Resource state
     */
    @SecondaryKey(name = STATE_KEY_NAME, relate = Relationship.MANY_TO_ONE)
    protected ResourceState state;

    /**
     * The access mode
     */
    private LockMode lockmode;

    /**
     * The dependencies for this job (dependencies are on any resource)
     * This array is filled when needed.
     */
    private transient TreeMap<ResourceLocator, Dependency> dependencies = null;

    /**
     * Task manager
     */
    transient Scheduler scheduler;

    /**
     * Number of unsatisfied dependencies
     */
    int nbUnsatisfied;
    /**
     * Number of dependencies that are in an "error" state
     * This is needed to updateFromStatusFile the "on hold" state
     */
    int nbHolding;


    @Override
    protected void finalize() {
        LOGGER.debug("Finalizing resource [%s@%s]", System.identityHashCode(this), this);
    }


    /**
     * If the resource is currently locked
     */
    boolean locked;

    /**
     * Called when deserializing from database
     */
    protected Resource() {
    }

    /**
     * Constructs a resource
     *
     * @param scheduler The scheduler
     * @param connector The connectorId to the resource
     * @param path      The path to the resource
     * @param mode      The locking mode
     */
    public Resource(Scheduler scheduler, Connector connector, String path, LockMode mode) {
        this(scheduler, new ResourceLocator(connector, path), mode);
    }

    public Resource(Scheduler scheduler, ResourceLocator identifier, LockMode lockMode) {
        this.scheduler = scheduler;
        this.locator = identifier;
        this.lockmode = lockMode;
        this.dependencies = new TreeMap<>();
        LOGGER.debug("Constructor of resource [%s@%s]", System.identityHashCode(this), this);
    }


    /**
     * Compares to another resource (based on the from)
     */
    public int compareTo(Resource o) {
        return locator.compareTo(o.locator);
    }

    @Override
    public int hashCode() {
        return locator.hashCode();
    }

    @Override
    public String toString() {
        return  locator.toString();
    }

    /**
     * Update the status of the resource by checking all that could have changed externally
     * <p/>
     * Calls {@linkplain #doUpdateStatus(sf.net.experimaestro.scheduler.Resource.Changes)}. If a change is detected, then it updates
     * the representation of the resource in the database by calling {@linkplain Scheduler#store(XPMTransaction, Resource, sf.net.experimaestro.scheduler.Resource.Changes)}.
     * <p/>
     * If the update fails for some reason, then we just put the state into HOLD
     */
    final public boolean updateStatus(XPMTransaction txn, boolean store) throws ExperimaestroCannotOverwrite {
        Changes changes = new Changes();
        try {
             doUpdateStatus(changes);
        } catch (Exception e) {
            state = ResourceState.ON_HOLD;
            changes.state = true;
        }
        if (changes.changed() && store) {
            // TODO: should store only the needed parts
            scheduler.store(txn, this, changes);
        }
        return changes.changed();
    }

    /**
     * Update the status of this resource
     *
     * @return True if the status was updated
     * @param changes
     */
    synchronized protected void doUpdateStatus(Changes changes) throws Exception {
        updateFromStatusFile(changes);

        // Check if the resource was generated
        final FileObject doneFile = getMainConnector().resolveFile(locator.getPath() + DONE_EXTENSION);
        if (doneFile.exists() && this.getState() != ResourceState.DONE) {
            changes.state = true;
            if (this instanceof Job) {
                ((Job)this).endTimestamp = doneFile.getContent().getLastModifiedTime();
            }
            this.state = ResourceState.DONE;
        }

        // Check if the resource is locked
        boolean locked = getMainConnector().resolveFile(locator.getPath() + LOCK_EXTENSION).exists();
        // TODO: should be a locking change
        changes.state |= locked != this.locked;
        this.locked = locked;

    }

    /**
     * Returns the connector associated to this resource
     *
     * @return a Connector object
     */
    final public Connector getConnector() {
        return locator.getConnector();
    }

    /**
     * Returns the main connector associated with this resource
     *
     * @return a SingleHostConnector object
     */
    public final SingleHostConnector getMainConnector() {
        return getConnector().getMainConnector();
    }


    /**
     * Initialise a resource when retrieved from database. Does nothing if the resource
     * is already initialized
     *
     * @return <tt>true</tt> if the object was initialized, <tt>false</tt> if it was already
     */
    public boolean init(Scheduler scheduler) throws DatabaseException {
        if (this.scheduler != null)
            return false;

        this.scheduler = scheduler;
        this.locator.init(scheduler);
        return true;
    }


    /**
     * States in which a resource can replaced
     */
    final static EnumSet<ResourceState> UPDATABLE_STATES
            = EnumSet.of(ResourceState.READY, ResourceState.ON_HOLD, ResourceState.ERROR, ResourceState.WAITING);

    /**
     * Replace this resource
     *
     * @param old
     * @return true if the resource was replaced, and false if an error occured
     */
    public boolean replace(XPMTransaction txn, Resource old) throws ExperimaestroCannotOverwrite {
        synchronized (old) {
            assert old.locator.equals(locator) : "locators do not match";
            if (UPDATABLE_STATES.contains(old.state)) {
                scheduler.store(txn, this, null);
                return true;
            }
        }
        return false;
    }

    /**
     * Notifies the resource that something happened
     *
     * @param resource
     * @param objects
     */
    public void notify(Resource resource, Object... objects) {

    }

    public void setState(ResourceState state) {
        this.state = state;
    }


    /**
     * Sets the name
     *
     * @param group
     */
    public void setGroup(String group) {
        this.group = group;
    }

    public String getGroup() {
        return this.group;
    }


    /**
     * The set of dependencies for this object
     */
    public Collection<Dependency> getDependencies() {
        if (dependencies == null)
            dependencies = scheduler.getDependencies(getLocator());
        return dependencies.values();
    }

    /**
     * Add a dependency to another resource
     * <p/>
     * <b>Warning</b>: the database is not updated after such a call, it
     * is the responsability of the caller to do so
     *
     * @param resource The resource to lock
     * @param type     The type of lock that is asked
     */
    public void addDependency(Resource resource, LockType type) {
        LOGGER.info("Adding dependency %s to %s for %s", type, resource, this);
        final DependencyStatus accept = resource.accept(type);
        final ResourceState resourceState = resource.state;

        if (accept == DependencyStatus.ERROR)
            throw new RuntimeException(format(
                    "Resource %s cannot be satisfied for lock type %s",
                    resource, type));

        final boolean ready = accept.isOK();

        synchronized (this) {
            if (!ready)
                nbUnsatisfied++;
            // We copy the resource from, otherwise this object can never
            // be reclaimed
            final ResourceLocator from = new ResourceLocator(resource.locator);
            final ResourceLocator to = new ResourceLocator(this.getLocator());
            final Dependency dependency = new Dependency(from, to, type, ready, resourceState);
            getDependencies();
            dependencies.put(from, dependency);
        }
    }

    /**
     * Check if a dependency changed and returns whether this has changed the state of the job
     *
     * @param resource The resource to check
     * @param reset    If true, the stored dependecy status is reset (this is useful when doing a global update)
     * @return true if the resource changed, false otherwise
     */
    synchronized protected void checkDependency(Resource resource, boolean reset, Changes changes) {
        // Get the new state of the resource
        final ResourceState newResourceState = resource.getState();

        // Get the cached status
        Dependency status = getDependency(resource.getLocator());
        if (status == null) {
            // Log this
            nbHolding++;
            nbUnsatisfied++;
            LOGGER.error("Could not retrieve dependency %s", resource.getLocator());
            if (updateState(ResourceState.ON_HOLD)) changes.state = true;
            return;
        }

        // Resets the flag if needed
        if (reset) {
            status.isSatisfied = true;
        }

        // Is this resource in a state that is good for us?
        int k = resource.accept(status.type).isOK() ? 1 : 0;

        // Computes the difference with the previous status to updateFromStatusFile the number
        // of unsatisfied resources states
        final int diff = (status.isSatisfied ? 1 : 0) - k;

        // No change
        LOGGER.info("[%s] Checking dependency %s [ok=%d with lock=%s/diff=%d]", this,
                resource, k, status.type, diff);

        if (diff == 0)
            return;


        // If the resource has an error / hold state, change our state to
        // "on hold"
        if (newResourceState == ResourceState.ERROR || newResourceState == ResourceState.ON_HOLD) {

            if (!status.state.isBlocking())
                ++nbHolding;

            if (state != ResourceState.ON_HOLD) {
                LOGGER.debug("Putting resource [%s] on hold", this);
                state = ResourceState.ON_HOLD;
            }

        } else if (status.state.isBlocking()) {
            --nbHolding;
            if (nbHolding == 0 && this.state == ResourceState.ON_HOLD) {
                this.state = ResourceState.WAITING;
                LOGGER.debug("Resource [%s] state is WAITING", this);
            }
        }

        // Update
        nbUnsatisfied += diff;
        status.isSatisfied = k == 1;
        status.state = newResourceState;

        if (nbUnsatisfied == 0) {
            if (this.state == ResourceState.WAITING) {
                this.state = ResourceState.READY;
                LOGGER.debug("Resource [%s] state changed to READY", this);
            }
        } else {
            if (this.state == ResourceState.READY) {
                this.state = ResourceState.WAITING;
                LOGGER.debug("Resource [%s] state changed to WAITING", this);
            }
        }

        changes.state = true;
        LOGGER.debug("After checking, [%s] state is %s [u=%d, h=%d]", this, state, nbUnsatisfied, nbHolding);
    }

    /**
     * Change the state of the resource
     * @param state The new state
     * @return true if the state was changed
     */
    protected synchronized boolean updateState(ResourceState state) {
        if (state == this.state) return false;
        this.state = state;
        return true;
    }

    private Dependency getDependency(ResourceLocator locator) {
        if (dependencies == null)
            dependencies = scheduler.getDependencies(getLocator());

        return dependencies.get(locator);
    }

    public boolean retrievedDependencies() {
        return dependencies != null;
    }

    /** Invalidate the resource
     *
     * Put the state into waiting mode and clean all the output files
     */
    synchronized public void invalidate(XPMTransaction txn) throws Exception {
        if (state == ResourceState.DONE) {
            state = ResourceState.WAITING;
            clean();
            updateStatus(txn, false);
            scheduler.store(txn, this, Changes.STATE);
        }
    }

    /**
     * Defines how a given lock type is satisfied by the current state
     * of the resource
     */
    static public enum DependencyStatus {
        /**
         * The resource can be used as is
         */
        OK,

        /**
         * The resource can be used when properly locked
         */
        OK_LOCK,

        /**
         * The resource is not ready yet
         */
        WAIT,

        /**
         * The resource is not ready yet, and is on hold (this can only be changed
         * by the external intervention)
         */
        HOLD,

        /**
         * The resource is not ready, and this is due to an error (possibly among
         * dependencies)
         */
        ERROR;

        public boolean isOK() {
            return this == OK_LOCK || this == OK;
        }
    }

    /**
     * Can the dependency be accepted?
     *
     * @param locktype
     * @return {@link DependencyStatus#OK} if the dependency is satisfied,
     *         {@link DependencyStatus#WAIT} if it can be satisfied one day
     *         {@link DependencyStatus#HOLD} if it can be satisfied after an external change
     *         {@link DependencyStatus#ERROR} if it cannot be satisfied
     * @see {@linkplain }
     */
    DependencyStatus accept(LockType locktype) {
        LOGGER.debug("Checking lock %s for resource %s (state %s)",
                locktype, this, getState());

        // Handle simple cases
        if (state == ResourceState.ERROR)
            return DependencyStatus.HOLD;

        if (state == ResourceState.ON_HOLD)
            return DependencyStatus.HOLD;

        // If not done, then we wait
        if (state != ResourceState.DONE)
            return DependencyStatus.WAIT;

        // OK, we have to get a look into it
        switch (locktype) {
            case GENERATED:
                return getState() == ResourceState.DONE ? DependencyStatus.OK
                        : DependencyStatus.WAIT;

            case EXCLUSIVE_ACCESS:
                return writers == 0 && readers == 0 ? DependencyStatus.OK_LOCK
                        : DependencyStatus.WAIT;

            case READ_ACCESS:
                switch (lockmode) {
                    case EXCLUSIVE_WRITER:
                    case SINGLE_WRITER:
                        return writers == 0 ? DependencyStatus.OK
                                : DependencyStatus.WAIT;

                    case MULTIPLE_WRITER:
                        return DependencyStatus.OK;
                    case READ_ONLY:
                        return getState() == ResourceState.DONE ? DependencyStatus.OK
                                : DependencyStatus.WAIT;
                }
                break;

            // We need a write access
            case WRITE_ACCESS:
                switch (lockmode) {
                    case EXCLUSIVE_WRITER:
                        return writers == 0 && readers == 0 ? DependencyStatus.OK_LOCK
                                : DependencyStatus.WAIT;
                    case MULTIPLE_WRITER:
                        return DependencyStatus.OK;
                    case READ_ONLY:
                        return DependencyStatus.ERROR;
                    case SINGLE_WRITER:
                        return writers == 0 ? DependencyStatus.OK_LOCK
                                : DependencyStatus.WAIT;
                }
        }

        return DependencyStatus.ERROR;
    }

    /**
     * Tries to lock the resource depending on the type of dependency
     *
     * @param dependency
     * @throws UnlockableException
     */
    public Lock lock(String pid, LockType dependency) throws UnlockableException {
        // Check the dependency status
        switch (accept(dependency)) {
            // Cases where the lock cannot be granted (task waiting or in error)
            case WAIT:
                throw new UnlockableException(
                        "Cannot grant dependency %s for resource %s", dependency,
                        this);
            case ERROR:
                throw new RuntimeException(
                        format("Resource %s cannot accept dependency %s", this,
                                dependency));

            case OK_LOCK:
                // Case where we need a full lock
                return getMainConnector().createLockFile(locator + LOCK_EXTENSION);

            case OK:
                // Case where we just need a status lock
                return new StatusLock(this, pid, dependency == LockType.WRITE_ACCESS);

        }

        return null;

    }

    /**
     * @return the generated
     */
    public boolean isGenerated() {
        return getState() == ResourceState.DONE;
    }

    /**
     * TODO: this should be outside resource : it is part of the locking
     * mechanism --> create a Locking class that can update itself, etc.
     *
     * Number of readers and writers
     */
    int writers = 0, readers = 0;

    /**
     * Last check of the status file
     */
    long lastUpdate = 0;

    /**
     * Current list
     */
    transient TreeMap<String, Boolean> processMap = new TreeMap<>();

    public int getReaders() {
        return readers;
    }

    public int getWriters() {
        return writers;
    }


    /**
     * Get the task from
     *
     * @return The task unique from
     */
    public ResourceLocator getLocator() {
        return locator;
    }

    public String getIdentifier() {
        return locator.toString();
    }

    /**
     * Is the resource locked?
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * Get the state of the resource
     *
     * @return The current state of the resource
     */
    public ResourceState getState() {
        return state;
    }


    /**
     * Update the database after a change in state
     *
     * @return true if everything went well
     */
    boolean updateDb(XPMTransaction txn, Changes changes) {
        try {
            scheduler.store(txn, this, changes);
            return true;
        } catch (DatabaseException | ExperimaestroCannotOverwrite e) {
            LOGGER.error(
                    "Could not update the information in the database for %s: %s",
                    this, e.getMessage());
        }
        return false;
    }

    boolean updateDb(Changes changes) {
        return updateDb(null, changes);
    }

    /**
     * Defines how printing should be done
     */
    static public class PrintConfig {
        public String detailURL;
    }

    /**
     * Writes an XML description of the resource
     *
     * @param out
     * @param config The configuration for printing
     */
    public void printXML(PrintWriter out, PrintConfig config) {
        out.format("<div><b>Resource id</b>: %s</h2>", locator);
        out.format("<div><b>Status</b>: %s</div>", state);
    }


    /**
     * Update the status file
     *
     * @param pidFrom     The old PID (or 0 if none)
     * @param pidTo       The new PID (or 0 if none)
     * @param writeAccess True if we need the write access
     * @throws UnlockableException
     */
    public void updateStatusFile(String pidFrom, String pidTo, boolean writeAccess)
            throws UnlockableException {
        // --- Lock the resource
        Lock fileLock = getMainConnector().createLockFile(locator.path + LOCK_EXTENSION);

        try {
            // --- Read the resource status
            updateFromStatusFile(new Changes());

            final FileObject tmpFile = getMainConnector().resolveFile(locator.path + STATUS_EXTENSION + ".tmp");
            PrintWriter out = new PrintWriter(tmpFile.getContent().getOutputStream());
            if (pidFrom == null) {
                // We are adding a new entry
                out.format("%s %s%n", pidTo, writeAccess ? "w" : "r");
            } else {
                // We are modifying an entry: rewrite the file
                processMap.remove(pidFrom);
                if (pidTo != null)
                    processMap.put(pidTo, writeAccess);
                for (Map.Entry<String, Boolean> x : processMap.entrySet())
                    out.format("%s %s%n", x.getKey(), x.getValue() ? "w" : "r");
            }

            out.close();
            tmpFile.moveTo(getMainConnector().resolveFile(locator.path + STATUS_EXTENSION));
        } catch (Exception e) {
            throw new UnlockableException(
                    "Status file '%s' could not be created", locator.path + STATUS_EXTENSION);
        } finally {
            if (fileLock != null)
                fileLock.dispose();
        }

    }


    /**
     * Update the status of the resource using its status file
     *
     * @return A boolean specifying whether something was updated
     * @throws java.io.FileNotFoundException If some error occurs while reading status
     * @param changes
     */
    public void updateFromStatusFile(Changes changes) throws Exception {
        final FileObject statusFile = getMainConnector().resolveFile(locator.path + STATUS_EXTENSION);

        if (!statusFile.exists()) {
            writers = readers = 0;
            return;
        } else {
            // Check if we need to read the file
            long lastModified = statusFile.getContent().getLastModifiedTime();
            if (lastUpdate >= lastModified)
                return;

            lastUpdate = lastModified;

            try {
                for (String line : new ReadLineIterator(statusFile.getContent().getInputStream())) {
                    String[] fields = line.split("\\s+");
                    if (fields.length != 2)
                        LOGGER.error(
                                "Skipping line %s (wrong number of fields)",
                                line);
                    else {
                        if (fields[1].equals("r"))
                            readers += 1;
                        else if (fields[1].equals("w"))
                            writers += 1;
                        else
                            LOGGER.error("Skipping line %s (unkown mode %s)",
                                    fields[1]);

                        if (processMap != null)
                            processMap.put(fields[0], fields[1].equals("w"));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }

    /**
     * Called after creation when the task is ready
     */
    public void ready() {
        state = nbHolding == 0 && nbUnsatisfied == 0 ? ResourceState.READY : ResourceState.WAITING;
    }

    @Override
    public void clean() {
    }
}
