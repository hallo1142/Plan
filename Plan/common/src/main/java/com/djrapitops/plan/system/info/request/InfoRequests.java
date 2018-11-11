/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.system.info.request;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;

/**
 * Map object that holds {@link InfoRequest} objects used for handling incoming requests.
 * <p>
 * Convenience class for Dagger injection.
 *
 * @author Rsl1122
 */
@Singleton
public class InfoRequests extends HashMap<String, InfoRequest> {

    private final InfoRequestHandlerFactory handlers;

    @Inject
    public InfoRequests(InfoRequestHandlerFactory handlers) {
        this.handlers = handlers;
    }

    public void initializeRequests() {
        putRequest(handlers.cacheAnalysisPageRequest());
        putRequest(handlers.cacheInspectPageRequest());
        putRequest(handlers.cacheInspectPluginsTabRequest());
        putRequest(handlers.cacheNetworkPageContentRequest());

        putRequest(handlers.generateAnalysisPageRequest());
        putRequest(handlers.generateInspectPageRequest());
        putRequest(handlers.generateInspectPluginsTabRequest());

        putRequest(handlers.saveDBSettingsRequest());
        putRequest(handlers.sendDBSettingsRequest());
        putRequest(handlers.checkConnectionRequest());
    }

    private void putRequest(InfoRequest request) {
        put(request.getClass().getSimpleName().toLowerCase(), request);
    }
}