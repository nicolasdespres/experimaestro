package sf.net.experimaestro.scheduler;

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

import org.json.simple.JSONObject;
import sf.net.experimaestro.manager.scripting.Expose;
import sf.net.experimaestro.manager.scripting.Exposed;
import sf.net.experimaestro.utils.log.Logger;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.PostLoad;
import java.io.IOException;

/**
 * A class that can be locked a given number of times at the same time.
 * <p>
 * This is useful when one wants status limit the number of processes on a host for
 * example
 *
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 * @date 23/11/12
 */
@Entity
@DiscriminatorValue(Resource.TOKEN_RESOURCE_TYPE)
@Exposed
public class TokenResource extends Resource {
    final static private Logger LOGGER = Logger.getLogger();

    /**
     * Maximum number of tokens available
     */
    private int limit;

    /**
     * Number of used tokens
     */
    private int usedTokens;

    /**
     * Keeps track of the old state
     */
    transient boolean wasBlocking;

    protected TokenResource() {
    }

    @PostLoad
    protected void postLoad() {
        super.postLoad();
        wasBlocking = isBlocking();
    }

    /**
     * Creates a new token resource
     *
     * @param path  The token path
     * @param limit The maximum number of tokens
     */
    public TokenResource(String path, int limit) {
        super(null, path);
        this.limit = limit;
        this.usedTokens = 0;
        this.wasBlocking = isBlocking();
        setState(ResourceState.DONE);
    }

    public String toDetailedString() {
        return String.format("%s [%d/%d]", super.toDetailedString(), usedTokens, limit);
    }

    public int getLimit() {
        return limit;
    }

    public int getUsedTokens() {
        return usedTokens;
    }

    @Override
    public JSONObject toJSON() throws IOException {
        JSONObject info = super.toJSON();
        JSONObject tokenInfo = new JSONObject();
        tokenInfo.put("used", usedTokens);
        tokenInfo.put("limit", limit);
        info.put("tokens", tokenInfo);
        return info;
    }

    @Override
    synchronized protected boolean doUpdateStatus() throws Exception {
        LOGGER.debug("Updating token resource");
        int used = 0;
        for (Dependency dependency : getDependencies()) {
            if (dependency.hasLock()) {
                LOGGER.debug("Dependency [%s] has lock", dependency);
                used++;
            }
        }

        if (used != this.usedTokens) {
            this.usedTokens = used;
            return true;
        }

        return false;
    }


    @Override
    public TokenDependency createDependency(Object values) {
        return new TokenDependency(this);
    }

    /**
     * Unlock a resource
     *
     * @return
     */
    synchronized void unlock() {
        if (usedTokens >= 0) {
            --usedTokens;
            LOGGER.debug("Releasing one token (%s/%s) [version %d]", usedTokens, limit, version);
        } else {
            LOGGER.warn("Attempt to release non existent token (%d/%d) [version %d]", usedTokens, limit, version);
            usedTokens = 0;
        }
    }

    public void increaseUsedTokens() {
        ++usedTokens;
        LOGGER.debug("Getting one more token (%s/%s) [version %d]", usedTokens, limit, version);
    }

    @Override
    public void stored() {
        // Notify scheduler state has changed
        if (wasBlocking && !isBlocking()) {
            LOGGER.debug("Token %s is not blocking anymore: notifying scheduler",
                    this, wasBlocking, isBlocking());
            Scheduler.get().notifyRunners();
        }
    }

    private boolean isBlocking() {
        return usedTokens >= limit;
    }

    @Expose("set_limit")
    public void setLimit(final int limit) {
        // Get a database copy of this resource first
        Transaction.run((em, t) -> {
            this.lock(t, true);
            TokenResource self = em.find(TokenResource.class, getId());
            if (limit != self.limit) {
                self.limit = limit;
                if (!isBlocking()) {
                    // Notify runners if nothing
                    Scheduler.notifyRunners();
                }
            }
        });
    }

}
