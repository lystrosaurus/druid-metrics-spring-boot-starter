package io.github.lystrosaurus.metrics.jdbc;

import com.alibaba.druid.pool.DruidDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.function.ToDoubleFunction;
import javax.sql.DataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceUnwrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogMessage;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for metrics on all available
 * {@link DataSource datasources}.
 *
 * @author Stephane Nicoll
 * @since 2.0.0
 */
@AutoConfigureAfter({MetricsAutoConfiguration.class, DataSourceAutoConfiguration.class,
    SimpleMetricsExportAutoConfiguration.class})
@ConditionalOnClass({DataSource.class, DruidDataSource.class, MeterRegistry.class})
@ConditionalOnBean({DataSource.class, MeterRegistry.class})
@Configuration(proxyBeanMethods = false)
public class DruidDataSourceMetricsConfiguration {

  @Bean
  DuridDataSourceMeterBinder duridDataSourceMeterBinder(ObjectProvider<DataSource> dataSources) {
    return new DuridDataSourceMeterBinder(dataSources);
  }

  static class DuridDataSourceMeterBinder implements MeterBinder {

    private static final Log logger = LogFactory.getLog(DuridDataSourceMeterBinder.class);

    private final ObjectProvider<DataSource> dataSources;

    DuridDataSourceMeterBinder(ObjectProvider<DataSource> dataSources) {
      this.dataSources = dataSources;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
      for (DataSource dataSource : this.dataSources) {
        DruidDataSource druidDataSource = DataSourceUnwrapper.unwrap(dataSource,
            DruidDataSource.class);
        if (druidDataSource != null) {
          bindMetricsRegistryToDruidDataSource(druidDataSource, registry);
        }
      }
    }

    private void bindMetricsRegistryToDruidDataSource(DruidDataSource druid,
        MeterRegistry registry) {
      try {
        DruidCollector druidCollector = new DruidCollector(druid, registry);
        druidCollector.register();
      } catch (Exception ex) {
        logger.warn(LogMessage.format("Failed to bind Druid metrics: %s", ex.getMessage()));
      }
    }
  }

  static class DruidCollector {

    private static final String LABEL_NAME = "pool";

    private final DruidDataSource druidDataSource;

    private final MeterRegistry registry;

    DruidCollector(DruidDataSource dataSource, MeterRegistry registry) {
      this.registry = registry;
      this.druidDataSource = dataSource;
    }

    void register() {

      // basic configurations
      createGauge(druidDataSource, "druid_initial_size", "Initial size",
          datasource -> (double) datasource.getInitialSize());
      createGauge(druidDataSource, "druid_min_idle", "Min idle",
          datasource -> (double) datasource.getMinIdle());
      createGauge(druidDataSource, "druid_max_active", "Max active",
          datasource -> (double) datasource.getMaxActive());

      // connection pool core metrics
      createGauge(druidDataSource, "druid_active_count", "Active count",
          datasource -> (double) datasource.getActiveCount());
      createGauge(druidDataSource, "druid_active_peak", "Active peak",
          datasource -> (double) datasource.getActivePeak());
      createGauge(druidDataSource, "druid_pooling_peak", "Pooling peak",
          datasource -> (double) datasource.getPoolingPeak());
      createGauge(druidDataSource, "druid_pooling_count", "Pooling count",
          datasource -> (double) datasource.getPoolingCount());
      createGauge(druidDataSource, "druid_wait_thread_count", "Wait thread count",
          datasource -> (double) datasource.getWaitThreadCount());

      // connection pool detail metrics
      createGauge(druidDataSource, "druid_not_empty_wait_count", "Not empty wait count",
          datasource -> (double) datasource.getNotEmptyWaitCount());
      createGauge(druidDataSource, "druid_not_empty_wait_millis", "Not empty wait millis",
          datasource -> (double) datasource.getNotEmptyWaitMillis());
      createGauge(druidDataSource, "druid_not_empty_thread_count", "Not empty thread count",
          datasource -> (double) datasource.getNotEmptyWaitThreadCount());

      createGauge(druidDataSource, "druid_logic_connect_count", "Logic connect count",
          datasource -> (double) datasource.getConnectCount());
      createGauge(druidDataSource, "druid_logic_close_count", "Logic close count",
          datasource -> (double) datasource.getCloseCount());
      createGauge(druidDataSource, "druid_logic_connect_error_count", "Logic connect error count",
          datasource -> (double) datasource.getConnectErrorCount());
      createGauge(druidDataSource, "druid_physical_connect_count", "Physical connect count",
          datasource -> (double) datasource.getCreateCount());
      createGauge(druidDataSource, "druid_physical_close_count", "Physical close count",
          datasource -> (double) datasource.getDestroyCount());
      createGauge(druidDataSource, "druid_physical_connect_error_count",
          "Physical connect error count",
          datasource -> (double) datasource.getCreateErrorCount());

      // sql execution core metrics
      createGauge(druidDataSource, "druid_error_count", "Error count",
          datasource -> (double) datasource.getErrorCount());
      createGauge(druidDataSource, "druid_execute_count", "Execute count",
          datasource -> (double) datasource.getExecuteCount());
      // transaction metrics
      createGauge(druidDataSource, "druid_start_transaction_count", "Start transaction count",
          datasource -> (double) datasource.getStartTransactionCount());
      createGauge(druidDataSource, "druid_commit_count", "Commit count",
          datasource -> (double) datasource.getCommitCount());
      createGauge(druidDataSource, "druid_rollback_count", "Rollback count",
          datasource -> (double) datasource.getRollbackCount());

      // sql execution detail
      createGauge(druidDataSource, "druid_prepared_statement_open_count",
          "Prepared statement open count",
          datasource -> (double) datasource.getPreparedStatementCount());
      createGauge(druidDataSource, "druid_prepared_statement_closed_count",
          "Prepared statement closed count",
          datasource -> (double) datasource.getClosedPreparedStatementCount());
      createGauge(druidDataSource, "druid_ps_cache_access_count", "PS cache access count",
          datasource -> (double) datasource.getCachedPreparedStatementAccessCount());
      createGauge(druidDataSource, "druid_ps_cache_hit_count", "PS cache hit count",
          datasource -> (double) datasource.getCachedPreparedStatementHitCount());
      createGauge(druidDataSource, "druid_ps_cache_miss_count", "PS cache miss count",
          datasource -> (double) datasource.getCachedPreparedStatementMissCount());
      createGauge(druidDataSource, "druid_execute_query_count", "Execute query count",
          datasource -> (double) datasource.getExecuteQueryCount());
      createGauge(druidDataSource, "druid_execute_update_count", "Execute update count",
          datasource -> (double) datasource.getExecuteUpdateCount());
      createGauge(druidDataSource, "druid_execute_batch_count", "Execute batch count",
          datasource -> (double) datasource.getExecuteBatchCount());

      // none core metrics, some are static configurations
      createGauge(druidDataSource, "druid_max_wait", "Max wait",
          datasource -> (double) datasource.getMaxWait());
      createGauge(druidDataSource, "druid_max_wait_thread_count", "Max wait thread count",
          datasource -> (double) datasource.getMaxWaitThreadCount());
      createGauge(druidDataSource, "druid_login_timeout", "Login timeout",
          datasource -> (double) datasource.getLoginTimeout());
      createGauge(druidDataSource, "druid_query_timeout", "Query timeout",
          datasource -> (double) datasource.getQueryTimeout());
      createGauge(druidDataSource, "druid_transaction_query_timeout", "Transaction query timeout",
          datasource -> (double) datasource.getTransactionQueryTimeout());
    }

    private void createGauge(DruidDataSource weakRef, String metric, String help,
        ToDoubleFunction<DruidDataSource> measure) {
      Gauge.builder(metric, weakRef, measure)
          .description(help)
          .tag(LABEL_NAME, weakRef.getName())
          .register(this.registry);
    }
  }
}
