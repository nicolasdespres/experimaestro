package sf.net.experimaestro.connectors;

/*
 * This file is part of experimaestro.
 * Copyright (c) 2014 B. Piwowarski <benjamin@bpiwowar.net>
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

import sf.net.experimaestro.exceptions.LockException;
import sf.net.experimaestro.fs.XPMPath;
import sf.net.experimaestro.locks.Lock;
import sf.net.experimaestro.scheduler.Transaction;

import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.String.format;
import static sf.net.experimaestro.utils.Functional.propagate;
import static sf.net.experimaestro.utils.Functional.propagateFunction;

/**
 * A connector that corresponds to a single host.
 * <p/>
 * Descendant of this class of connector provide access to a file system and to a process launcher.
 *
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 */
@Entity
@DiscriminatorColumn(name = "type")
abstract public class SingleHostConnector extends Connector {
    /**
     * Underlying filesystem
     */
    transient private FileSystem filesystem;

    public SingleHostConnector(String id) {
        super(id);
    }


    protected SingleHostConnector() {
    }

    @Override
    public SingleHostConnector getConnector(ComputationalRequirements requirements) {
        // By default, returns ourselves - TODO: check the requirements
        return this;
    }

    @Override
    public SingleHostConnector getMainConnector() {
        // By default, the main connector is ourselves
        return this;
    }

    /**
     * Get the underlying filesystem
     */
    protected abstract FileSystem doGetFileSystem() throws IOException;

    /**
     * Get the underlying file system
     *
     * @return
     * @throws FileSystemException
     */
    public FileSystem getFileSystem() throws IOException {
        if (filesystem == null)
            return filesystem = doGetFileSystem();
        return filesystem;
    }

    /**
     * Resolve the file name
     *
     * @param path The path to the file
     * @return A file object
     * @throws FileSystemException
     */
    public Path resolveFile(String path) throws IOException {
        return getFileSystem().getPath(path);
    }

    /**
     * Resolve a Path to a local path
     * <p/>
     * Throws an exception when the file name cannot be resolved, i.e. when
     * the file object is not
     */
    public String resolve(Path file) throws IOException {
        if (file instanceof XPMPath) {
            String resolve = Transaction.evaluate(propagateFunction(em -> {
                XPMPath xpmPath = (XPMPath) file;
                NetworkShareAccess access = NetworkShare.find(em, this, xpmPath);
                if (access != null) {
                    return access.resolve(xpmPath);
                }
                return null;
            }));
            if (resolve != null) {
                return resolve;
            }
        }

        if (!contains(file.getFileSystem())) {
            throw new FileSystemException(format("Cannot resolve file %s within filesystem %s", file, this));
        }

        return file.toString();
    }

    /**
     * Returns true if the filesystem matches
     */
    protected abstract boolean contains(FileSystem fileSystem) throws IOException;

    /**
     * Returns a process builder
     */
    public abstract AbstractProcessBuilder processBuilder();

    /**
     * Lock a file
     */
    public abstract Lock createLockFile(Path path, boolean wait) throws LockException;

    /**
     * Returns the hostname
     */
    public abstract String getHostName();


    public Path getTemporaryFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(getTemporaryDirectory(), prefix, suffix);
    }

    protected abstract Path getTemporaryDirectory() throws IOException;

    @Override
    public Path resolve(String path) throws IOException {
        return getFileSystem().getPath(path);
    }

    /**
     * Creates a script builder
     * @param scriptFile The path to the script file to createSSHAgentIdentityRepository
     * @return A builder
     * @throws FileSystemException if an exception occurs while accessing the script file
     */
    abstract public XPMScriptProcessBuilder scriptProcessBuilder(Path scriptFile)
            throws IOException;
}
