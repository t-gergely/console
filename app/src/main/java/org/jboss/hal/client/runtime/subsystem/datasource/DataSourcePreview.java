/*
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.hal.client.runtime.subsystem.datasource;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import org.jboss.gwt.elemento.core.Elements;
import org.jboss.hal.ballroom.Alert;
import org.jboss.hal.ballroom.metric.Utilization;
import org.jboss.hal.config.Environment;
import org.jboss.hal.core.datasource.DataSource;
import org.jboss.hal.core.finder.PreviewContent;
import org.jboss.hal.core.runtime.server.Server;
import org.jboss.hal.core.runtime.server.ServerActions;
import org.jboss.hal.dmr.ModelNode;
import org.jboss.hal.dmr.ModelNodeHelper;
import org.jboss.hal.dmr.dispatch.Dispatcher;
import org.jboss.hal.dmr.model.Composite;
import org.jboss.hal.dmr.model.CompositeResult;
import org.jboss.hal.dmr.model.Operation;
import org.jboss.hal.dmr.model.ResourceAddress;
import org.jboss.hal.meta.AddressTemplate;
import org.jboss.hal.meta.StatementContext;
import org.jboss.hal.resources.Icons;
import org.jboss.hal.resources.Resources;

import static org.jboss.gwt.elemento.core.EventType.click;
import static org.jboss.hal.dmr.ModelDescriptionConstants.ATTRIBUTES_ONLY;
import static org.jboss.hal.dmr.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.hal.dmr.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.hal.dmr.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.hal.dmr.ModelDescriptionConstants.RESULT;
import static org.jboss.hal.resources.CSS.*;

/**
 * @author Harald Pehl
 */
class DataSourcePreview extends PreviewContent<DataSource> {

    private final Server server;
    private final DataSource dataSource;
    private final Environment environment;
    private final Dispatcher dispatcher;
    private final StatementContext statementContext;
    private final ResourceAddress dataSourceAddress;

    private final Alert needsReloadWarning;
    private final Alert needsRestartWarning;
    private final Alert disabledWarning;
    private final Alert noStatisticsWarning;
    private final Utilization activeConnections;
    private final Utilization maxUsedConnections;
    private final Utilization hitCount;
    private final Utilization missCount;

    DataSourcePreview(final DataSourceColumn column, final Server server, final DataSource dataSource,
            final Environment environment, final Dispatcher dispatcher, final StatementContext statementContext,
            final ServerActions serverActions, final Resources resources) {
        super(dataSource.getName());
        this.server = server;
        this.dataSource = dataSource;
        this.environment = environment;
        this.dispatcher = dispatcher;
        this.statementContext = statementContext;
        this.dataSourceAddress = column.dataSourceAddress(dataSource);

        needsReloadWarning = new Alert(Icons.WARNING,
                new SafeHtmlBuilder()
                        .append(resources.messages().serverNeedsReload(server.getName()))
                        .appendEscaped(" ")
                        .append(resources.messages().staleStatistics())
                        .toSafeHtml(),
                resources.constants().reload(), event -> serverActions.reload(server));
        previewBuilder().add(needsReloadWarning);

        needsRestartWarning = new Alert(Icons.WARNING,
                new SafeHtmlBuilder()
                        .append(resources.messages().serverNeedsRestart(server.getName()))
                        .appendEscaped(" ")
                        .append(resources.messages().staleStatistics())
                        .toSafeHtml(),
                resources.constants().restart(), event -> serverActions.restart(server));
        previewBuilder().add(needsRestartWarning);

        noStatisticsWarning = new Alert(Icons.WARNING,
                resources.messages().dataSourceStatisticsDisabled(dataSource.getName()),
                resources.constants().enableStatistics(), event -> column.enableStatistics(dataSource));
        previewBuilder().add(noStatisticsWarning);

        disabledWarning = new Alert(Icons.WARNING,
                resources.messages().dataSourceDisabledNoStatistics(dataSource.getName()),
                resources.constants().enable(), event -> column.enableDataSource(dataSource));
        previewBuilder().add(disabledWarning);

        activeConnections = new Utilization(resources.constants().active(), resources.constants().connections(), false,
                true);
        maxUsedConnections = new Utilization(resources.constants().maxUsed(), resources.constants().connections(),
                false, true);
        hitCount = new Utilization(resources.constants().hitCount(), resources.constants().count(), false, false);
        missCount = new Utilization(resources.constants().missCount(), resources.constants().count(), false, false);

        // @formatter:off
        previewBuilder()
            .p()
                .a().css(clickable, pullRight).on(click, event -> update(null))
                    .span().css(fontAwesome("refresh"), marginRight4).end()
                    .span().textContent(resources.constants().refresh()).end()
                .end()
            .end()
            .h(2).css(underline).textContent(resources.constants().connectionPool()).end()
            .add(activeConnections)
            .add(maxUsedConnections)
            .h(2).css(underline).textContent(resources.constants().preparedStatementCache()).end()
            .add(hitCount)
            .add(missCount);
        // @formatter:on
    }

    @Override
    @SuppressWarnings("HardCodedStringLiteral")
    public void update(final DataSource ds) {
        List<Operation> operations = new ArrayList<>();

        if (environment.isStandalone()) {
            operations.add(new Operation.Builder(READ_RESOURCE_OPERATION, ResourceAddress.ROOT)
                    .param(INCLUDE_RUNTIME, true)
                    .param(ATTRIBUTES_ONLY, true)
                    .build());
        } else {
            ResourceAddress address = AddressTemplate.of("/{selected.host}/{selected.server}")
                    .resolve(statementContext);
            operations.add(new Operation.Builder(READ_RESOURCE_OPERATION, address)
                    .param(INCLUDE_RUNTIME, true)
                    .param(ATTRIBUTES_ONLY, true)
                    .build());
        }
        if (ds == null) {
            operations.add(new Operation.Builder(READ_RESOURCE_OPERATION, dataSourceAddress)
                    .param(INCLUDE_RUNTIME, true)
                    .param(RECURSIVE, true)
                    .build());
        }

        dispatcher.execute(new Composite(operations), (CompositeResult result) -> {
            server.addServerAttributes(result.step(0).get(RESULT));
            if (ds == null) {
                dataSource.update(result.step(1).get(RESULT));
            }
            
            Elements.setVisible(needsReloadWarning.asElement(), false);
            Elements.setVisible(needsRestartWarning.asElement(), false);
            Elements.setVisible(noStatisticsWarning.asElement(), false);
            Elements.setVisible(disabledWarning.asElement(), false);

            if (!dataSource.isStatisticsEnabled()) {
                Elements.setVisible(noStatisticsWarning.asElement(), true);
            } else if (!dataSource.isEnabled()) {
                Elements.setVisible(disabledWarning.asElement(), true);
            } else {
                Elements.setVisible(needsReloadWarning.asElement(), server.needsReload());
                Elements.setVisible(needsRestartWarning.asElement(), server.needsRestart());
            }

            // pool statistics
            ModelNode pool = ModelNodeHelper.failSafeGet(dataSource, "statistics/pool");
            if (pool.isDefined()) {
                int available = pool.get("AvailableCount").asInt(0);
                int active1 = pool.get("ActiveCount").asInt(0);
                int maxUsed = pool.get("MaxUsedCount").asInt(0);
                activeConnections.update(active1, available);
                maxUsedConnections.update(maxUsed, available);
            } else {
                activeConnections.update(0, 0);
                maxUsedConnections.update(0, 0);
            }
            boolean disableUsage = !dataSource.isStatisticsEnabled() || !dataSource.isEnabled() || !pool.isDefined();
            activeConnections.setDisabled(disableUsage);
            maxUsedConnections.setDisabled(disableUsage);

            // jdbc statistics
            ModelNode jdbc = ModelNodeHelper.failSafeGet(dataSource, "statistics/jdbc");
            if (jdbc.isDefined()) {
                long accessed = jdbc.get("PreparedStatementCacheAccessCount").asLong(0);
                long hit = jdbc.get("PreparedStatementCacheHitCount").asLong(0);
                long missed = jdbc.get("PreparedStatementCacheMissCount").asLong(0);
                hitCount.update(hit, accessed);
                missCount.update(missed, accessed);
            } else {
                hitCount.update(0, 0);
                missCount.update(0, 0);
            }
            disableUsage = !dataSource.isStatisticsEnabled() || !dataSource.isEnabled() || !jdbc.isDefined();
            hitCount.setDisabled(disableUsage);
            missCount.setDisabled(disableUsage);
        });
    }
}