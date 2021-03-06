/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.plugin.cassandra;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.json.JsonModule;
import io.prestosql.spi.connector.Connector;
import io.prestosql.spi.connector.ConnectorContext;
import io.prestosql.spi.connector.ConnectorFactory;
import io.prestosql.spi.connector.ConnectorHandleResolver;
import org.weakref.jmx.guice.MBeanModule;

import javax.management.MBeanServer;

import java.lang.management.ManagementFactory;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.util.Objects.requireNonNull;

public class CassandraConnectorFactory
        implements ConnectorFactory
{
    private final String name;

    public CassandraConnectorFactory(String name)
    {
        checkArgument(!isNullOrEmpty(name), "name is null or empty");
        this.name = name;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public ConnectorHandleResolver getHandleResolver()
    {
        return new CassandraHandleResolver();
    }

    @Override
    public Connector create(String catalogName, Map<String, String> config, ConnectorContext context)
    {
        requireNonNull(config, "config is null");

        try {
            Bootstrap app = new Bootstrap(
                    new MBeanModule(),
                    new JsonModule(),
                    new CassandraClientModule(),
                    new Module()
                    {
                        @Override
                        public void configure(Binder binder)
                        {
                            MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
                            binder.bind(MBeanServer.class).toInstance(new RebindSafeMBeanServer(platformMBeanServer));
                        }
                    });

            Injector injector = app.strictConfig().doNotInitializeLogging()
                    .setRequiredConfigurationProperties(config)
                    .initialize();

            return injector.getInstance(CassandraConnector.class);
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }
}
