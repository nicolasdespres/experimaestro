/*
 *
 *  This file is part of experimaestro.
 *  Copyright (c) 2012 B. Piwowarski <benjamin@bpiwowar.net>
 *
 *  experimaestro is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  experimaestro is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with experimaestro.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package sf.net.experimaestro.scheduler;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.persist.model.KeyField;
import com.sleepycat.persist.model.Persistent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import sf.net.experimaestro.connectors.Connector;
import sf.net.experimaestro.exceptions.ExperimaestroRuntimeException;

/**
 * Identifies a resource on the network
 *
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 * @date 13/6/12
 */
@Persistent
public class ResourceLocator implements Comparable<ResourceLocator> {
    @KeyField(value = 1)
    String connectorId;

    @KeyField(value = 2)
    String path;

    /**
     * Connector
     */
    transient Connector connector;

    public ResourceLocator() {
    }

    /**
     * Initialization with a connector id. The object needs to be initialized
     * with {@linkplain #init(Scheduler)}
     *
     * @param path The path to the resource on the main connector
     */
    public ResourceLocator(String path) {
        this.path = path;
    }

    public ResourceLocator(ResourceLocator other) {
        this.connectorId = other.connectorId;
        this.connector = other.connector;
        this.path = other.path;
    }

    void init(Scheduler scheduler) throws DatabaseException {
        connector = scheduler.getConnector(connectorId);
    }

    public String getPath() {
        return path;
    }

    public ResourceLocator(Connector connector, String path) {
        this.connectorId = connector.getIdentifier();
        this.path = path;
        this.connector = connector;
    }

    @Override
    public int compareTo(ResourceLocator other) {
        int z = connectorId.compareTo(other.connectorId);
        if (z != 0)
            return z;
        return path.compareTo(path);
    }

    @Override
    public String toString() {
        return String.format("%s%s", connectorId, path);
    }

//    public static ResourceLocator decode(String idString) {
//        URI uri = null;
//        try {
//            uri = new URI(idString);
//        } catch (URISyntaxException e) {
//            throw new RuntimeException(e);
//        }
//
//        if ("local".equals(uri.getScheme()))
//            return LocalhostConnector.getIdentifier(uri);
//
//        if ("xpm".equals(uri.getScheme())) {
//            throw new IllegalArgumentException("xpm URI scheme is not supported yet");
//        }
//
//        if ("ssh".equals(uri.getScheme()))
//            return SSHConnector.getIdentifier(uri);
//
//        throw new RuntimeException(String.format("Unknown scheme [%s] in URL [%s]", uri.getScheme(), idString));
//    }


    public Connector getConnector() {
        if (connector == null)
            throw new ExperimaestroRuntimeException("The locator has not been properly initialized");

        return connector;
    }

    /**
     * Resolve a path relative to this resource
     * @param path The path
     * @param parent Is the path relative to the parent?
     * @return A new resource locator object
     * @throws FileSystemException If something goes wrong while accessing the file system
     */
    public ResourceLocator resolvePath(String path, boolean parent) throws FileSystemException {
        FileObject file = connector.getMainConnector().resolveFile(this.path);
        FileObject target = (parent ? file.getParent() : file).resolveFile(path);
        return new ResourceLocator(connector, file.getName().getPath());
    }

    public FileObject getFile() throws FileSystemException {
        return connector.getMainConnector().resolveFile(path);
    }
}