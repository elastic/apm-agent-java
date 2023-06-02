/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.esrestclient;

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EndpointResolutionHelper {

    @Nullable
    private static EndpointResolutionHelper INSTANCE;
    private final Map<String, String[]> routesMap = new HashMap<>();
    private final Map<String, Pattern> regexPatternMap = new ConcurrentHashMap<>();
    private EndpointResolutionHelper() {
        // This map is generated from the Java client code.
        // It's a one-time generation that shouldn't require maintenance effort,
        // as in the future the ES client will come with a native instrumentation.
        routesMap.put("es/async_search.status", new String[]{"/_async_search/status/{id}"});
        routesMap.put("es/indices.analyze", new String[]{"/_analyze", "/{index}/_analyze"});
        routesMap.put("es/sql.clear_cursor", new String[]{"/_sql/close"});
        routesMap.put("es/ml.delete_datafeed", new String[]{"/_ml/datafeeds/{datafeed_id}"});
        routesMap.put("es/explain", new String[]{"/{index}/_explain/{id}"});
        routesMap.put("es/cat.thread_pool", new String[]{"/_cat/thread_pool", "/_cat/thread_pool/{thread_pool_patterns}"});
        routesMap.put("es/ml.delete_calendar", new String[]{"/_ml/calendars/{calendar_id}"});
        routesMap.put("es/indices.create_data_stream", new String[]{"/_data_stream/{name}"});
        routesMap.put("es/cat.fielddata", new String[]{"/_cat/fielddata", "/_cat/fielddata/{fields}"});
        routesMap.put("es/security.enroll_node", new String[]{"/_security/enroll/node"});
        routesMap.put("es/slm.get_status", new String[]{"/_slm/status"});
        routesMap.put("es/ml.put_calendar", new String[]{"/_ml/calendars/{calendar_id}"});
        routesMap.put("es/create", new String[]{"/{index}/_create/{id}"});
        routesMap.put("es/ml.preview_datafeed", new String[]{"/_ml/datafeeds/{datafeed_id}/_preview", "/_ml/datafeeds/_preview"});
        routesMap.put("es/indices.put_template", new String[]{"/_template/{name}"});
        routesMap.put("es/nodes.reload_secure_settings", new String[]{"/_nodes/reload_secure_settings", "/_nodes/{node_id}/reload_secure_settings"});
        routesMap.put("es/indices.delete_data_stream", new String[]{"/_data_stream/{name}"});
        routesMap.put("es/transform.schedule_now_transform", new String[]{"/_transform/{transform_id}/_schedule_now"});
        routesMap.put("es/slm.stop", new String[]{"/_slm/stop"});
        routesMap.put("es/rollup.delete_job", new String[]{"/_rollup/job/{id}"});
        routesMap.put("es/cluster.put_component_template", new String[]{"/_component_template/{name}"});
        routesMap.put("es/delete_script", new String[]{"/_scripts/{id}"});
        routesMap.put("es/ml.delete_trained_model", new String[]{"/_ml/trained_models/{model_id}"});
        routesMap.put("es/indices.simulate_template", new String[]{"/_index_template/_simulate", "/_index_template/_simulate/{name}"});
        routesMap.put("es/slm.get_lifecycle", new String[]{"/_slm/policy/{policy_id}", "/_slm/policy"});
        routesMap.put("es/security.enroll_kibana", new String[]{"/_security/enroll/kibana"});
        routesMap.put("es/fleet.search", new String[]{"/{index}/_fleet/_fleet_search"});
        routesMap.put("es/reindex_rethrottle", new String[]{"/_reindex/{task_id}/_rethrottle"});
        routesMap.put("es/ml.update_filter", new String[]{"/_ml/filters/{filter_id}/_update"});
        routesMap.put("es/rollup.get_rollup_caps", new String[]{"/_rollup/data/{id}", "/_rollup/data"});
        routesMap.put("es/ccr.resume_auto_follow_pattern", new String[]{"/_ccr/auto_follow/{name}/resume"});
        routesMap.put("es/features.get_features", new String[]{"/_features"});
        routesMap.put("es/slm.get_stats", new String[]{"/_slm/stats"});
        routesMap.put("es/indices.clear_cache", new String[]{"/_cache/clear", "/{index}/_cache/clear"});
        routesMap.put("es/cluster.post_voting_config_exclusions", new String[]{"/_cluster/voting_config_exclusions"});
        routesMap.put("es/index", new String[]{"/{index}/_doc/{id}", "/{index}/_doc"});
        routesMap.put("es/cat.pending_tasks", new String[]{"/_cat/pending_tasks"});
        routesMap.put("es/indices.promote_data_stream", new String[]{"/_data_stream/_promote/{name}"});
        routesMap.put("es/ml.delete_filter", new String[]{"/_ml/filters/{filter_id}"});
        routesMap.put("es/sql.query", new String[]{"/_sql"});
        routesMap.put("es/ccr.follow_stats", new String[]{"/{index}/_ccr/stats"});
        routesMap.put("es/transform.stop_transform", new String[]{"/_transform/{transform_id}/_stop"});
        routesMap.put("es/security.has_privileges_user_profile", new String[]{"/_security/profile/_has_privileges"});
        routesMap.put("es/autoscaling.delete_autoscaling_policy", new String[]{"/_autoscaling/policy/{name}"});
        routesMap.put("es/scripts_painless_execute", new String[]{"/_scripts/painless/_execute"});
        routesMap.put("es/indices.delete", new String[]{"/{index}"});
        routesMap.put("es/security.clear_cached_roles", new String[]{"/_security/role/{name}/_clear_cache"});
        routesMap.put("es/eql.delete", new String[]{"/_eql/search/{id}"});
        routesMap.put("es/update", new String[]{"/{index}/_update/{id}"});
        routesMap.put("es/snapshot.clone", new String[]{"/_snapshot/{repository}/{snapshot}/_clone/{target_snapshot}"});
        routesMap.put("es/license.get_basic_status", new String[]{"/_license/basic_status"});
        routesMap.put("es/indices.close", new String[]{"/{index}/_close"});
        routesMap.put("es/security.saml_authenticate", new String[]{"/_security/saml/authenticate"});
        routesMap.put("es/search_application.put", new String[]{"/_application/search_application/{name}"});
        routesMap.put("es/count", new String[]{"/_count", "/{index}/_count"});
        routesMap.put("es/migration.deprecations", new String[]{"/_migration/deprecations", "/{index}/_migration/deprecations"});
        routesMap.put("es/indices.segments", new String[]{"/_segments", "/{index}/_segments"});
        routesMap.put("es/security.suggest_user_profiles", new String[]{"/_security/profile/_suggest"});
        routesMap.put("es/security.get_user_privileges", new String[]{"/_security/user/_privileges"});
        routesMap.put("es/indices.delete_alias", new String[]{"/{index}/_alias/{name}", "/{index}/_aliases/{name}"});
        routesMap.put("es/indices.get_mapping", new String[]{"/_mapping", "/{index}/_mapping"});
        routesMap.put("es/indices.put_index_template", new String[]{"/_index_template/{name}"});
        routesMap.put("es/searchable_snapshots.stats", new String[]{"/_searchable_snapshots/stats", "/{index}/_searchable_snapshots/stats"});
        routesMap.put("es/security.disable_user", new String[]{"/_security/user/{username}/_disable"});
        routesMap.put("es/ml.upgrade_job_snapshot", new String[]{"/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_upgrade"});
        routesMap.put("es/delete", new String[]{"/{index}/_doc/{id}"});
        routesMap.put("es/async_search.delete", new String[]{"/_async_search/{id}"});
        routesMap.put("es/cat.transforms", new String[]{"/_cat/transforms", "/_cat/transforms/{transform_id}"});
        routesMap.put("es/ping", new String[]{"/"});
        routesMap.put("es/ccr.pause_auto_follow_pattern", new String[]{"/_ccr/auto_follow/{name}/pause"});
        routesMap.put("es/indices.shard_stores", new String[]{"/_shard_stores", "/{index}/_shard_stores"});
        routesMap.put("es/ml.update_data_frame_analytics", new String[]{"/_ml/data_frame/analytics/{id}/_update"});
        routesMap.put("es/logstash.delete_pipeline", new String[]{"/_logstash/pipeline/{id}"});
        routesMap.put("es/sql.translate", new String[]{"/_sql/translate"});
        routesMap.put("es/exists", new String[]{"/{index}/_doc/{id}"});
        routesMap.put("es/snapshot.get_repository", new String[]{"/_snapshot", "/_snapshot/{repository}"});
        routesMap.put("es/snapshot.verify_repository", new String[]{"/_snapshot/{repository}/_verify"});
        routesMap.put("es/indices.put_data_lifecycle", new String[]{"/_data_stream/{name}/_lifecycle"});
        routesMap.put("es/ml.open_job", new String[]{"/_ml/anomaly_detectors/{job_id}/_open"});
        routesMap.put("es/security.update_user_profile_data", new String[]{"/_security/profile/{uid}/_data"});
        routesMap.put("es/enrich.put_policy", new String[]{"/_enrich/policy/{name}"});
        routesMap.put("es/ml.get_datafeed_stats", new String[]{"/_ml/datafeeds/{datafeed_id}/_stats", "/_ml/datafeeds/_stats"});
        routesMap.put("es/open_point_in_time", new String[]{"/{index}/_pit"});
        routesMap.put("es/get_source", new String[]{"/{index}/_source/{id}"});
        routesMap.put("es/delete_by_query", new String[]{"/{index}/_delete_by_query"});
        routesMap.put("es/security.create_api_key", new String[]{"/_security/api_key"});
        routesMap.put("es/cat.tasks", new String[]{"/_cat/tasks"});
        routesMap.put("es/watcher.delete_watch", new String[]{"/_watcher/watch/{id}"});
        routesMap.put("es/ingest.processor_grok", new String[]{"/_ingest/processor/grok"});
        routesMap.put("es/ingest.put_pipeline", new String[]{"/_ingest/pipeline/{id}"});
        routesMap.put("es/ml.get_data_frame_analytics_stats", new String[]{"/_ml/data_frame/analytics/_stats", "/_ml/data_frame/analytics/{id}/_stats"});
        routesMap.put("es/indices.data_streams_stats", new String[]{"/_data_stream/_stats", "/_data_stream/{name}/_stats"});
        routesMap.put("es/security.clear_cached_realms", new String[]{"/_security/realm/{realms}/_clear_cache"});
        routesMap.put("es/field_caps", new String[]{"/_field_caps", "/{index}/_field_caps"});
        routesMap.put("es/ml.evaluate_data_frame", new String[]{"/_ml/data_frame/_evaluate"});
        routesMap.put("es/ml.delete_forecast", new String[]{"/_ml/anomaly_detectors/{job_id}/_forecast", "/_ml/anomaly_detectors/{job_id}/_forecast/{forecast_id}"});
        routesMap.put("es/enrich.get_policy", new String[]{"/_enrich/policy/{name}", "/_enrich/policy"});
        routesMap.put("es/rollup.start_job", new String[]{"/_rollup/job/{id}/_start"});
        routesMap.put("es/tasks.cancel", new String[]{"/_tasks/_cancel", "/_tasks/{task_id}/_cancel"});
        routesMap.put("es/security.saml_logout", new String[]{"/_security/saml/logout"});
        routesMap.put("es/render_search_template", new String[]{"/_render/template", "/_render/template/{id}"});
        routesMap.put("es/ml.get_calendar_events", new String[]{"/_ml/calendars/{calendar_id}/events"});
        routesMap.put("es/security.enable_user_profile", new String[]{"/_security/profile/{uid}/_enable"});
        routesMap.put("es/logstash.get_pipeline", new String[]{"/_logstash/pipeline", "/_logstash/pipeline/{id}"});
        routesMap.put("es/cat.snapshots", new String[]{"/_cat/snapshots", "/_cat/snapshots/{repository}"});
        routesMap.put("es/indices.add_block", new String[]{"/{index}/_block/{block}"});
        routesMap.put("es/terms_enum", new String[]{"/{index}/_terms_enum"});
        routesMap.put("es/ml.forecast", new String[]{"/_ml/anomaly_detectors/{job_id}/_forecast"});
        routesMap.put("es/cluster.stats", new String[]{"/_cluster/stats", "/_cluster/stats/nodes/{node_id}"});
        routesMap.put("es/search_application.list", new String[]{"/_application/search_application"});
        routesMap.put("es/cat.count", new String[]{"/_cat/count", "/_cat/count/{index}"});
        routesMap.put("es/cat.segments", new String[]{"/_cat/segments", "/_cat/segments/{index}"});
        routesMap.put("es/ccr.resume_follow", new String[]{"/{index}/_ccr/resume_follow"});
        routesMap.put("es/search_application.get", new String[]{"/_application/search_application/{name}"});
        routesMap.put("es/security.saml_service_provider_metadata", new String[]{"/_security/saml/metadata/{realm_name}"});
        routesMap.put("es/update_by_query", new String[]{"/{index}/_update_by_query"});
        routesMap.put("es/ml.stop_datafeed", new String[]{"/_ml/datafeeds/{datafeed_id}/_stop"});
        routesMap.put("es/ilm.explain_lifecycle", new String[]{"/{index}/_ilm/explain"});
        routesMap.put("es/ml.put_trained_model_vocabulary", new String[]{"/_ml/trained_models/{model_id}/vocabulary"});
        routesMap.put("es/indices.exists", new String[]{"/{index}"});
        routesMap.put("es/ml.set_upgrade_mode", new String[]{"/_ml/set_upgrade_mode"});
        routesMap.put("es/security.saml_invalidate", new String[]{"/_security/saml/invalidate"});
        routesMap.put("es/ml.get_job_stats", new String[]{"/_ml/anomaly_detectors/_stats", "/_ml/anomaly_detectors/{job_id}/_stats"});
        routesMap.put("es/cluster.allocation_explain", new String[]{"/_cluster/allocation/explain"});
        routesMap.put("es/watcher.activate_watch", new String[]{"/_watcher/watch/{watch_id}/_activate"});
        routesMap.put("es/searchable_snapshots.clear_cache", new String[]{"/_searchable_snapshots/cache/clear", "/{index}/_searchable_snapshots/cache/clear"});
        routesMap.put("es/msearch_template", new String[]{"/_msearch/template", "/{index}/_msearch/template"});
        routesMap.put("es/bulk", new String[]{"/_bulk", "/{index}/_bulk"});
        routesMap.put("es/cat.nodeattrs", new String[]{"/_cat/nodeattrs"});
        routesMap.put("es/indices.get_index_template", new String[]{"/_index_template", "/_index_template/{name}"});
        routesMap.put("es/license.get", new String[]{"/_license"});
        routesMap.put("es/ccr.forget_follower", new String[]{"/{index}/_ccr/forget_follower"});
        routesMap.put("es/security.delete_role", new String[]{"/_security/role/{name}"});
        routesMap.put("es/indices.validate_query", new String[]{"/_validate/query", "/{index}/_validate/query"});
        routesMap.put("es/tasks.get", new String[]{"/_tasks/{task_id}"});
        routesMap.put("es/ml.start_data_frame_analytics", new String[]{"/_ml/data_frame/analytics/{id}/_start"});
        routesMap.put("es/indices.create", new String[]{"/{index}"});
        routesMap.put("es/cluster.delete_voting_config_exclusions", new String[]{"/_cluster/voting_config_exclusions"});
        routesMap.put("es/info", new String[]{"/"});
        routesMap.put("es/watcher.stop", new String[]{"/_watcher/_stop"});
        routesMap.put("es/enrich.delete_policy", new String[]{"/_enrich/policy/{name}"});
        routesMap.put("es/cat.ml_data_frame_analytics", new String[]{"/_cat/ml/data_frame/analytics", "/_cat/ml/data_frame/analytics/{id}"});
        routesMap.put("es/security.change_password", new String[]{"/_security/user/{username}/_password", "/_security/user/_password"});
        routesMap.put("es/put_script", new String[]{"/_scripts/{id}", "/_scripts/{id}/{context}"});
        routesMap.put("es/ml.put_datafeed", new String[]{"/_ml/datafeeds/{datafeed_id}"});
        routesMap.put("es/cat.master", new String[]{"/_cat/master"});
        routesMap.put("es/features.reset_features", new String[]{"/_features/_reset"});
        routesMap.put("es/indices.get_data_lifecycle", new String[]{"/_data_stream/{name}/_lifecycle"});
        routesMap.put("es/ml.get_data_frame_analytics", new String[]{"/_ml/data_frame/analytics/{id}", "/_ml/data_frame/analytics"});
        routesMap.put("es/security.delete_service_token", new String[]{"/_security/service/{namespace}/{service}/credential/token/{name}"});
        routesMap.put("es/indices.recovery", new String[]{"/_recovery", "/{index}/_recovery"});
        routesMap.put("es/cat.recovery", new String[]{"/_cat/recovery", "/_cat/recovery/{index}"});
        routesMap.put("es/indices.downsample", new String[]{"/{index}/_downsample/{target_index}"});
        routesMap.put("es/ingest.delete_pipeline", new String[]{"/_ingest/pipeline/{id}"});
        routesMap.put("es/async_search.get", new String[]{"/_async_search/{id}"});
        routesMap.put("es/eql.get", new String[]{"/_eql/search/{id}"});
        routesMap.put("es/cat.aliases", new String[]{"/_cat/aliases", "/_cat/aliases/{name}"});
        routesMap.put("es/security.get_service_credentials", new String[]{"/_security/service/{namespace}/{service}/credential"});
        routesMap.put("es/cat.allocation", new String[]{"/_cat/allocation", "/_cat/allocation/{node_id}"});
        routesMap.put("es/ml.stop_data_frame_analytics", new String[]{"/_ml/data_frame/analytics/{id}/_stop"});
        routesMap.put("es/indices.open", new String[]{"/{index}/_open"});
        routesMap.put("es/ilm.get_lifecycle", new String[]{"/_ilm/policy/{policy}", "/_ilm/policy"});
        routesMap.put("es/ilm.remove_policy", new String[]{"/{index}/_ilm/remove"});
        routesMap.put("es/security.get_role_mapping", new String[]{"/_security/role_mapping/{name}", "/_security/role_mapping"});
        routesMap.put("es/snapshot.create", new String[]{"/_snapshot/{repository}/{snapshot}"});
        routesMap.put("es/watcher.get_watch", new String[]{"/_watcher/watch/{id}"});
        routesMap.put("es/license.post_start_trial", new String[]{"/_license/start_trial"});
        routesMap.put("es/snapshot.restore", new String[]{"/_snapshot/{repository}/{snapshot}/_restore"});
        routesMap.put("es/indices.put_mapping", new String[]{"/{index}/_mapping"});
        routesMap.put("es/ml.delete_calendar_job", new String[]{"/_ml/calendars/{calendar_id}/jobs/{job_id}"});
        routesMap.put("es/security.clear_api_key_cache", new String[]{"/_security/api_key/{ids}/_clear_cache"});
        routesMap.put("es/slm.start", new String[]{"/_slm/start"});
        routesMap.put("es/cat.component_templates", new String[]{"/_cat/component_templates", "/_cat/component_templates/{name}"});
        routesMap.put("es/security.enable_user", new String[]{"/_security/user/{username}/_enable"});
        routesMap.put("es/cluster.delete_component_template", new String[]{"/_component_template/{name}"});
        routesMap.put("es/security.get_role", new String[]{"/_security/role/{name}", "/_security/role"});
        routesMap.put("es/ingest.get_pipeline", new String[]{"/_ingest/pipeline", "/_ingest/pipeline/{id}"});
        routesMap.put("es/ml.delete_expired_data", new String[]{"/_ml/_delete_expired_data/{job_id}", "/_ml/_delete_expired_data"});
        routesMap.put("es/indices.get_settings", new String[]{"/_settings", "/{index}/_settings", "/{index}/_settings/{name}", "/_settings/{name}"});
        routesMap.put("es/ccr.follow", new String[]{"/{index}/_ccr/follow"});
        routesMap.put("es/termvectors", new String[]{"/{index}/_termvectors/{id}", "/{index}/_termvectors"});
        routesMap.put("es/ml.post_data", new String[]{"/_ml/anomaly_detectors/{job_id}/_data"});
        routesMap.put("es/eql.search", new String[]{"/{index}/_eql/search"});
        routesMap.put("es/ml.get_trained_models", new String[]{"/_ml/trained_models/{model_id}", "/_ml/trained_models"});
        routesMap.put("es/security.disable_user_profile", new String[]{"/_security/profile/{uid}/_disable"});
        routesMap.put("es/security.put_privileges", new String[]{"/_security/privilege"});
        routesMap.put("es/cat.nodes", new String[]{"/_cat/nodes"});
        routesMap.put("es/nodes.info", new String[]{"/_nodes", "/_nodes/{node_id}", "/_nodes/{metric}", "/_nodes/{node_id}/{metric}"});
        routesMap.put("es/graph.explore", new String[]{"/{index}/_graph/explore"});
        routesMap.put("es/autoscaling.put_autoscaling_policy", new String[]{"/_autoscaling/policy/{name}"});
        routesMap.put("es/cat.templates", new String[]{"/_cat/templates", "/_cat/templates/{name}"});
        routesMap.put("es/cluster.remote_info", new String[]{"/_remote/info"});
        routesMap.put("es/rank_eval", new String[]{"/_rank_eval", "/{index}/_rank_eval"});
        routesMap.put("es/security.delete_privileges", new String[]{"/_security/privilege/{application}/{name}"});
        routesMap.put("es/security.get_privileges", new String[]{"/_security/privilege", "/_security/privilege/{application}", "/_security/privilege/{application}/{name}"});
        routesMap.put("es/scroll", new String[]{"/_search/scroll"});
        routesMap.put("es/license.delete", new String[]{"/_license"});
        routesMap.put("es/indices.disk_usage", new String[]{"/{index}/_disk_usage"});
        routesMap.put("es/msearch", new String[]{"/_msearch", "/{index}/_msearch"});
        routesMap.put("es/indices.field_usage_stats", new String[]{"/{index}/_field_usage_stats"});
        routesMap.put("es/indices.rollover", new String[]{"/{alias}/_rollover", "/{alias}/_rollover/{new_index}"});
        routesMap.put("es/cat.ml_trained_models", new String[]{"/_cat/ml/trained_models", "/_cat/ml/trained_models/{model_id}"});
        routesMap.put("es/ml.delete_trained_model_alias", new String[]{"/_ml/trained_models/{model_id}/model_aliases/{model_alias}"});
        routesMap.put("es/indices.get", new String[]{"/{index}"});
        routesMap.put("es/sql.get_async_status", new String[]{"/_sql/async/status/{id}"});
        routesMap.put("es/ilm.stop", new String[]{"/_ilm/stop"});
        routesMap.put("es/security.put_user", new String[]{"/_security/user/{username}"});
        routesMap.put("es/cluster.state", new String[]{"/_cluster/state", "/_cluster/state/{metric}", "/_cluster/state/{metric}/{index}"});
        routesMap.put("es/indices.put_settings", new String[]{"/_settings", "/{index}/_settings"});
        routesMap.put("es/knn_search", new String[]{"/{index}/_knn_search"});
        routesMap.put("es/get", new String[]{"/{index}/_doc/{id}"});
        routesMap.put("es/eql.get_status", new String[]{"/_eql/search/status/{id}"});
        routesMap.put("es/ssl.certificates", new String[]{"/_ssl/certificates"});
        routesMap.put("es/ml.get_model_snapshots", new String[]{"/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}", "/_ml/anomaly_detectors/{job_id}/model_snapshots"});
        routesMap.put("es/nodes.clear_repositories_metering_archive", new String[]{"/_nodes/{node_id}/_repositories_metering/{max_archive_version}"});
        routesMap.put("es/security.put_role", new String[]{"/_security/role/{name}"});
        routesMap.put("es/ml.get_influencers", new String[]{"/_ml/anomaly_detectors/{job_id}/results/influencers"});
        routesMap.put("es/transform.upgrade_transforms", new String[]{"/_transform/_upgrade"});
        routesMap.put("es/ml.delete_calendar_event", new String[]{"/_ml/calendars/{calendar_id}/events/{event_id}"});
        routesMap.put("es/indices.get_field_mapping", new String[]{"/_mapping/field/{fields}", "/{index}/_mapping/field/{fields}"});
        routesMap.put("es/transform.preview_transform", new String[]{"/_transform/{transform_id}/_preview", "/_transform/_preview"});
        routesMap.put("es/tasks.list", new String[]{"/_tasks"});
        routesMap.put("es/ml.clear_trained_model_deployment_cache", new String[]{"/_ml/trained_models/{model_id}/deployment/cache/_clear"});
        routesMap.put("es/cluster.reroute", new String[]{"/_cluster/reroute"});
        routesMap.put("es/security.saml_complete_logout", new String[]{"/_security/saml/complete_logout"});
        routesMap.put("es/indices.simulate_index_template", new String[]{"/_index_template/_simulate_index/{name}"});
        routesMap.put("es/snapshot.get", new String[]{"/_snapshot/{repository}/{snapshot}"});
        routesMap.put("es/ccr.put_auto_follow_pattern", new String[]{"/_ccr/auto_follow/{name}"});
        routesMap.put("es/nodes.hot_threads", new String[]{"/_nodes/hot_threads", "/_nodes/{node_id}/hot_threads"});
        routesMap.put("es/ml.preview_data_frame_analytics", new String[]{"/_ml/data_frame/analytics/_preview", "/_ml/data_frame/analytics/{id}/_preview"});
        routesMap.put("es/indices.flush", new String[]{"/_flush", "/{index}/_flush"});
        routesMap.put("es/cluster.exists_component_template", new String[]{"/_component_template/{name}"});
        routesMap.put("es/snapshot.status", new String[]{"/_snapshot/_status", "/_snapshot/{repository}/_status", "/_snapshot/{repository}/{snapshot}/_status"});
        routesMap.put("es/ml.update_datafeed", new String[]{"/_ml/datafeeds/{datafeed_id}/_update"});
        routesMap.put("es/indices.update_aliases", new String[]{"/_aliases"});
        routesMap.put("es/autoscaling.get_autoscaling_capacity", new String[]{"/_autoscaling/capacity"});
        routesMap.put("es/migration.post_feature_upgrade", new String[]{"/_migration/system_features"});
        routesMap.put("es/ml.get_records", new String[]{"/_ml/anomaly_detectors/{job_id}/results/records"});
        routesMap.put("es/indices.get_alias", new String[]{"/_alias", "/_alias/{name}", "/{index}/_alias/{name}", "/{index}/_alias"});
        routesMap.put("es/logstash.put_pipeline", new String[]{"/_logstash/pipeline/{id}"});
        routesMap.put("es/snapshot.delete_repository", new String[]{"/_snapshot/{repository}"});
        routesMap.put("es/security.has_privileges", new String[]{"/_security/user/_has_privileges", "/_security/user/{user}/_has_privileges"});
        routesMap.put("es/cat.indices", new String[]{"/_cat/indices", "/_cat/indices/{index}"});
        routesMap.put("es/ccr.get_auto_follow_pattern", new String[]{"/_ccr/auto_follow", "/_ccr/auto_follow/{name}"});
        routesMap.put("es/ml.start_datafeed", new String[]{"/_ml/datafeeds/{datafeed_id}/_start"});
        routesMap.put("es/indices.clone", new String[]{"/{index}/_clone/{target}"});
        routesMap.put("es/search_application.delete", new String[]{"/_application/search_application/{name}"});
        routesMap.put("es/security.query_api_keys", new String[]{"/_security/_query/api_key"});
        routesMap.put("es/ml.flush_job", new String[]{"/_ml/anomaly_detectors/{job_id}/_flush"});
        routesMap.put("es/security.clear_cached_privileges", new String[]{"/_security/privilege/{application}/_clear_cache"});
        routesMap.put("es/indices.exists_index_template", new String[]{"/_index_template/{name}"});
        routesMap.put("es/indices.explain_data_lifecycle", new String[]{"/{index}/_lifecycle/explain"});
        routesMap.put("es/indices.put_alias", new String[]{"/{index}/_alias/{name}", "/{index}/_aliases/{name}"});
        routesMap.put("es/ml.get_buckets", new String[]{"/_ml/anomaly_detectors/{job_id}/results/buckets/{timestamp}", "/_ml/anomaly_detectors/{job_id}/results/buckets"});
        routesMap.put("es/ml.put_trained_model_definition_part", new String[]{"/_ml/trained_models/{model_id}/definition/{part}"});
        routesMap.put("es/get_script", new String[]{"/_scripts/{id}"});
        routesMap.put("es/ingest.simulate", new String[]{"/_ingest/pipeline/_simulate", "/_ingest/pipeline/{id}/_simulate"});
        routesMap.put("es/indices.migrate_to_data_stream", new String[]{"/_data_stream/_migrate/{name}"});
        routesMap.put("es/enrich.execute_policy", new String[]{"/_enrich/policy/{name}/_execute"});
        routesMap.put("es/indices.split", new String[]{"/{index}/_split/{target}"});
        routesMap.put("es/ml.delete_model_snapshot", new String[]{"/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}"});
        routesMap.put("es/nodes.usage", new String[]{"/_nodes/usage", "/_nodes/{node_id}/usage", "/_nodes/usage/{metric}", "/_nodes/{node_id}/usage/{metric}"});
        routesMap.put("es/cat.help", new String[]{"/_cat"});
        routesMap.put("es/ml.estimate_model_memory", new String[]{"/_ml/anomaly_detectors/_estimate_model_memory"});
        routesMap.put("es/exists_source", new String[]{"/{index}/_source/{id}"});
        routesMap.put("es/ml.put_data_frame_analytics", new String[]{"/_ml/data_frame/analytics/{id}"});
        routesMap.put("es/security.put_role_mapping", new String[]{"/_security/role_mapping/{name}"});
        routesMap.put("es/rollup.get_rollup_index_caps", new String[]{"/{index}/_rollup/data"});
        routesMap.put("es/transform.reset_transform", new String[]{"/_transform/{transform_id}/_reset"});
        routesMap.put("es/ml.infer_trained_model", new String[]{"/_ml/trained_models/{model_id}/_infer", "/_ml/trained_models/{model_id}/deployment/_infer"});
        routesMap.put("es/reindex", new String[]{"/_reindex"});
        routesMap.put("es/ml.put_trained_model", new String[]{"/_ml/trained_models/{model_id}"});
        routesMap.put("es/cat.ml_jobs", new String[]{"/_cat/ml/anomaly_detectors", "/_cat/ml/anomaly_detectors/{job_id}"});
        routesMap.put("es/search_application.search", new String[]{"/_application/search_application/{name}/_search"});
        routesMap.put("es/ilm.put_lifecycle", new String[]{"/_ilm/policy/{policy}"});
        routesMap.put("es/security.get_token", new String[]{"/_security/oauth2/token"});
        routesMap.put("es/ilm.move_to_step", new String[]{"/_ilm/move/{index}"});
        routesMap.put("es/search_template", new String[]{"/_search/template", "/{index}/_search/template"});
        routesMap.put("es/indices.delete_data_lifecycle", new String[]{"/_data_stream/{name}/_lifecycle"});
        routesMap.put("es/indices.get_data_stream", new String[]{"/_data_stream", "/_data_stream/{name}"});
        routesMap.put("es/ml.get_filters", new String[]{"/_ml/filters", "/_ml/filters/{filter_id}"});
        routesMap.put("es/cat.ml_datafeeds", new String[]{"/_cat/ml/datafeeds", "/_cat/ml/datafeeds/{datafeed_id}"});
        routesMap.put("es/rollup.rollup_search", new String[]{"/{index}/_rollup_search"});
        routesMap.put("es/ml.put_job", new String[]{"/_ml/anomaly_detectors/{job_id}"});
        routesMap.put("es/update_by_query_rethrottle", new String[]{"/_update_by_query/{task_id}/_rethrottle"});
        routesMap.put("es/indices.delete_index_template", new String[]{"/_index_template/{name}"});
        routesMap.put("es/indices.reload_search_analyzers", new String[]{"/{index}/_reload_search_analyzers"});
        routesMap.put("es/cluster.get_settings", new String[]{"/_cluster/settings"});
        routesMap.put("es/cluster.put_settings", new String[]{"/_cluster/settings"});
        routesMap.put("es/transform.put_transform", new String[]{"/_transform/{transform_id}"});
        routesMap.put("es/watcher.stats", new String[]{"/_watcher/stats", "/_watcher/stats/{metric}"});
        routesMap.put("es/ccr.delete_auto_follow_pattern", new String[]{"/_ccr/auto_follow/{name}"});
        routesMap.put("es/mtermvectors", new String[]{"/_mtermvectors", "/{index}/_mtermvectors"});
        routesMap.put("es/license.post", new String[]{"/_license"});
        routesMap.put("es/xpack.info", new String[]{"/_xpack"});
        routesMap.put("es/dangling_indices.import_dangling_index", new String[]{"/_dangling/{index_uuid}"});
        routesMap.put("es/nodes.get_repositories_metering_info", new String[]{"/_nodes/{node_id}/_repositories_metering"});
        routesMap.put("es/transform.get_transform_stats", new String[]{"/_transform/{transform_id}/_stats"});
        routesMap.put("es/mget", new String[]{"/_mget", "/{index}/_mget"});
        routesMap.put("es/security.get_builtin_privileges", new String[]{"/_security/privilege/_builtin"});
        routesMap.put("es/ml.update_model_snapshot", new String[]{"/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_update"});
        routesMap.put("es/ml.info", new String[]{"/_ml/info"});
        routesMap.put("es/indices.exists_template", new String[]{"/_template/{name}"});
        routesMap.put("es/watcher.ack_watch", new String[]{"/_watcher/watch/{watch_id}/_ack", "/_watcher/watch/{watch_id}/_ack/{action_id}"});
        routesMap.put("es/security.get_user", new String[]{"/_security/user/{username}", "/_security/user"});
        routesMap.put("es/shutdown.get_node", new String[]{"/_nodes/shutdown", "/_nodes/{node_id}/shutdown"});
        routesMap.put("es/watcher.start", new String[]{"/_watcher/_start"});
        routesMap.put("es/indices.shrink", new String[]{"/{index}/_shrink/{target}"});
        routesMap.put("es/license.post_start_basic", new String[]{"/_license/start_basic"});
        routesMap.put("es/xpack.usage", new String[]{"/_xpack/usage"});
        routesMap.put("es/ilm.delete_lifecycle", new String[]{"/_ilm/policy/{policy}"});
        routesMap.put("es/ccr.follow_info", new String[]{"/{index}/_ccr/info"});
        routesMap.put("es/ml.put_calendar_job", new String[]{"/_ml/calendars/{calendar_id}/jobs/{job_id}"});
        routesMap.put("es/rollup.put_job", new String[]{"/_rollup/job/{id}"});
        routesMap.put("es/clear_scroll", new String[]{"/_search/scroll"});
        routesMap.put("es/ml.delete_data_frame_analytics", new String[]{"/_ml/data_frame/analytics/{id}"});
        routesMap.put("es/security.get_api_key", new String[]{"/_security/api_key"});
        routesMap.put("es/cat.health", new String[]{"/_cat/health"});
        routesMap.put("es/security.invalidate_token", new String[]{"/_security/oauth2/token"});
        routesMap.put("es/slm.delete_lifecycle", new String[]{"/_slm/policy/{policy_id}"});
        routesMap.put("es/ml.stop_trained_model_deployment", new String[]{"/_ml/trained_models/{model_id}/deployment/_stop"});
        routesMap.put("es/monitoring.bulk", new String[]{"/_monitoring/bulk", "/_monitoring/{type}/bulk"});
        routesMap.put("es/indices.stats", new String[]{"/_stats", "/_stats/{metric}", "/{index}/_stats", "/{index}/_stats/{metric}"});
        routesMap.put("es/searchable_snapshots.cache_stats", new String[]{"/_searchable_snapshots/cache/stats", "/_searchable_snapshots/{node_id}/cache/stats"});
        routesMap.put("es/async_search.submit", new String[]{"/_async_search", "/{index}/_async_search"});
        routesMap.put("es/rollup.get_jobs", new String[]{"/_rollup/job/{id}", "/_rollup/job"});
        routesMap.put("es/ml.revert_model_snapshot", new String[]{"/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_revert"});
        routesMap.put("es/transform.delete_transform", new String[]{"/_transform/{transform_id}"});
        routesMap.put("es/cluster.pending_tasks", new String[]{"/_cluster/pending_tasks"});
        routesMap.put("es/ml.get_model_snapshot_upgrade_stats", new String[]{"/_ml/anomaly_detectors/{job_id}/model_snapshots/{snapshot_id}/_upgrade/_stats"});
        routesMap.put("es/ml.get_categories", new String[]{"/_ml/anomaly_detectors/{job_id}/results/categories/{category_id}", "/_ml/anomaly_detectors/{job_id}/results/categories"});
        routesMap.put("es/ccr.pause_follow", new String[]{"/{index}/_ccr/pause_follow"});
        routesMap.put("es/security.authenticate", new String[]{"/_security/_authenticate"});
        routesMap.put("es/enrich.stats", new String[]{"/_enrich/_stats"});
        routesMap.put("es/ml.put_trained_model_alias", new String[]{"/_ml/trained_models/{model_id}/model_aliases/{model_alias}"});
        routesMap.put("es/ml.get_overall_buckets", new String[]{"/_ml/anomaly_detectors/{job_id}/results/overall_buckets"});
        routesMap.put("es/indices.get_template", new String[]{"/_template", "/_template/{name}"});
        routesMap.put("es/security.delete_role_mapping", new String[]{"/_security/role_mapping/{name}"});
        routesMap.put("es/ml.get_datafeeds", new String[]{"/_ml/datafeeds/{datafeed_id}", "/_ml/datafeeds"});
        routesMap.put("es/slm.execute_lifecycle", new String[]{"/_slm/policy/{policy_id}/_execute"});
        routesMap.put("es/close_point_in_time", new String[]{"/_pit"});
        routesMap.put("es/snapshot.cleanup_repository", new String[]{"/_snapshot/{repository}/_cleanup"});
        routesMap.put("es/autoscaling.get_autoscaling_policy", new String[]{"/_autoscaling/policy/{name}"});
        routesMap.put("es/slm.put_lifecycle", new String[]{"/_slm/policy/{policy_id}"});
        routesMap.put("es/ml.get_jobs", new String[]{"/_ml/anomaly_detectors/{job_id}", "/_ml/anomaly_detectors"});
        routesMap.put("es/ml.get_trained_models_stats", new String[]{"/_ml/trained_models/{model_id}/_stats", "/_ml/trained_models/_stats"});
        routesMap.put("es/ml.validate_detector", new String[]{"/_ml/anomaly_detectors/_validate/detector"});
        routesMap.put("es/watcher.put_watch", new String[]{"/_watcher/watch/{id}"});
        routesMap.put("es/transform.update_transform", new String[]{"/_transform/{transform_id}/_update"});
        routesMap.put("es/ml.post_calendar_events", new String[]{"/_ml/calendars/{calendar_id}/events"});
        routesMap.put("es/migration.get_feature_upgrade_status", new String[]{"/_migration/system_features"});
        routesMap.put("es/get_script_context", new String[]{"/_script_context"});
        routesMap.put("es/ml.put_filter", new String[]{"/_ml/filters/{filter_id}"});
        routesMap.put("es/ml.update_job", new String[]{"/_ml/anomaly_detectors/{job_id}/_update"});
        routesMap.put("es/ingest.geo_ip_stats", new String[]{"/_ingest/geoip/stats"});
        routesMap.put("es/security.delete_user", new String[]{"/_security/user/{username}"});
        routesMap.put("es/indices.unfreeze", new String[]{"/{index}/_unfreeze"});
        routesMap.put("es/snapshot.create_repository", new String[]{"/_snapshot/{repository}"});
        routesMap.put("es/cluster.get_component_template", new String[]{"/_component_template", "/_component_template/{name}"});
        routesMap.put("es/ilm.migrate_to_data_tiers", new String[]{"/_ilm/migrate_to_data_tiers"});
        routesMap.put("es/indices.refresh", new String[]{"/_refresh", "/{index}/_refresh"});
        routesMap.put("es/ml.get_calendars", new String[]{"/_ml/calendars", "/_ml/calendars/{calendar_id}"});
        routesMap.put("es/watcher.deactivate_watch", new String[]{"/_watcher/watch/{watch_id}/_deactivate"});
        routesMap.put("es/cluster.health", new String[]{"/_cluster/health", "/_cluster/health/{index}"});
        routesMap.put("es/dangling_indices.delete_dangling_index", new String[]{"/_dangling/{index_uuid}"});
        routesMap.put("es/health_report", new String[]{"/_health_report", "/_health_report/{feature}"});
        routesMap.put("es/watcher.query_watches", new String[]{"/_watcher/_query/watches"});
        routesMap.put("es/ccr.unfollow", new String[]{"/{index}/_ccr/unfollow"});
        routesMap.put("es/ml.validate", new String[]{"/_ml/anomaly_detectors/_validate"});
        routesMap.put("es/cat.plugins", new String[]{"/_cat/plugins"});
        routesMap.put("es/watcher.execute_watch", new String[]{"/_watcher/watch/{id}/_execute", "/_watcher/watch/_execute"});
        routesMap.put("es/search_shards", new String[]{"/_search_shards", "/{index}/_search_shards"});
        routesMap.put("es/cat.shards", new String[]{"/_cat/shards", "/_cat/shards/{index}"});
        routesMap.put("es/ml.delete_job", new String[]{"/_ml/anomaly_detectors/{job_id}"});
        routesMap.put("es/ilm.start", new String[]{"/_ilm/start"});
        routesMap.put("es/security.get_user_profile", new String[]{"/_security/profile/{uid}"});
        routesMap.put("es/indices.modify_data_stream", new String[]{"/_data_stream/_modify"});
        routesMap.put("es/indices.exists_alias", new String[]{"/_alias/{name}", "/{index}/_alias/{name}"});
        routesMap.put("es/rollup.stop_job", new String[]{"/_rollup/job/{id}/_stop"});
        routesMap.put("es/dangling_indices.list_dangling_indices", new String[]{"/_dangling"});
        routesMap.put("es/snapshot.delete", new String[]{"/_snapshot/{repository}/{snapshot}"});
        routesMap.put("es/security.activate_user_profile", new String[]{"/_security/profile/_activate"});
        routesMap.put("es/ml.start_trained_model_deployment", new String[]{"/_ml/trained_models/{model_id}/deployment/_start"});
        routesMap.put("es/transform.start_transform", new String[]{"/_transform/{transform_id}/_start"});
        routesMap.put("es/cat.repositories", new String[]{"/_cat/repositories"});
        routesMap.put("es/ilm.get_status", new String[]{"/_ilm/status"});
        routesMap.put("es/shutdown.delete_node", new String[]{"/_nodes/{node_id}/shutdown"});
        routesMap.put("es/nodes.stats", new String[]{"/_nodes/stats", "/_nodes/{node_id}/stats", "/_nodes/stats/{metric}", "/_nodes/{node_id}/stats/{metric}", "/_nodes/stats/{metric}/{index_metric}", "/_nodes/{node_id}/stats/{metric}/{index_metric}"});
        routesMap.put("es/get_script_languages", new String[]{"/_script_language"});
        routesMap.put("es/slm.execute_retention", new String[]{"/_slm/_execute_retention"});
        routesMap.put("es/security.get_service_accounts", new String[]{"/_security/service/{namespace}/{service}", "/_security/service/{namespace}", "/_security/service"});
        routesMap.put("es/shutdown.put_node", new String[]{"/_nodes/{node_id}/shutdown"});
        routesMap.put("es/indices.resolve_index", new String[]{"/_resolve/index/{name}"});
        routesMap.put("es/search", new String[]{"/_search", "/{index}/_search"});
        routesMap.put("es/sql.get_async", new String[]{"/_sql/async/{id}"});
        routesMap.put("es/delete_by_query_rethrottle", new String[]{"/_delete_by_query/{task_id}/_rethrottle"});
        routesMap.put("es/transform.get_transform", new String[]{"/_transform/{transform_id}", "/_transform"});
        routesMap.put("es/security.invalidate_api_key", new String[]{"/_security/api_key"});
        routesMap.put("es/security.saml_prepare_authentication", new String[]{"/_security/saml/prepare"});
        routesMap.put("es/ml.get_memory_stats", new String[]{"/_ml/memory/_stats", "/_ml/memory/{node_id}/_stats"});
        routesMap.put("es/ccr.stats", new String[]{"/_ccr/stats"});
        routesMap.put("es/indices.forcemerge", new String[]{"/_forcemerge", "/{index}/_forcemerge"});
        routesMap.put("es/indices.delete_template", new String[]{"/_template/{name}"});
        routesMap.put("es/sql.delete_async", new String[]{"/_sql/async/delete/{id}"});
        routesMap.put("es/security.update_api_key", new String[]{"/_security/api_key/{id}"});
        routesMap.put("es/security.create_service_token", new String[]{"/_security/service/{namespace}/{service}/credential/token/{name}", "/_security/service/{namespace}/{service}/credential/token"});
        routesMap.put("es/license.get_trial_status", new String[]{"/_license/trial_status"});
        routesMap.put("es/searchable_snapshots.mount", new String[]{"/_snapshot/{repository}/{snapshot}/_mount"});
        routesMap.put("es/security.grant_api_key", new String[]{"/_security/api_key/grant"});
        routesMap.put("es/ilm.retry", new String[]{"/{index}/_ilm/retry"});
        routesMap.put("es/ml.reset_job", new String[]{"/_ml/anomaly_detectors/{job_id}/_reset"});
        routesMap.put("es/ml.close_job", new String[]{"/_ml/anomaly_detectors/{job_id}/_close"});
        routesMap.put("es/ml.explain_data_frame_analytics", new String[]{"/_ml/data_frame/analytics/_explain", "/_ml/data_frame/analytics/{id}/_explain"});
        routesMap.put("es/security.clear_cached_service_tokens", new String[]{"/_security/service/{namespace}/{service}/credential/token/{name}/_clear_cache"});
        routesMap.put("es/search_mvt", new String[]{"/{index}/_mvt/{field}/{zoom}/{x}/{y}"});
    }

    protected static EndpointResolutionHelper get() {
        if (INSTANCE == null) {
            INSTANCE = new EndpointResolutionHelper();
        }
        return INSTANCE;
    }

    protected void enrichSpanWithRouteInformation(Span<?> span, String method, String endpointId, String urlPath) {
        String[] availableRoutes = routesMap.get(endpointId);
        if (availableRoutes == null || availableRoutes.length == 0) {
            return;
        }

        span.withOtelAttribute("db.operation", (endpointId.startsWith("es/") && endpointId.length() > 3) ? endpointId.substring(3) : endpointId);
        if (availableRoutes.length == 1) {
            enrichSpan(span, method, availableRoutes[0], urlPath);
        } else {
            int i = 0;
            boolean enriched = false;
            while (i < availableRoutes.length && !enriched) {
                enriched = enrichSpan(span, method, availableRoutes[i], urlPath);
                i++;
            }
        }
    }

    private Matcher matchUrl(String route, String urlPath) {
        if (!regexPatternMap.containsKey(route)) {
            StringBuilder regexStr = new StringBuilder();
            regexStr.append('^');
            regexStr.append(route.replace("{", "(?<").replace("}", ">[^/]+)"));
            regexStr.append('$');
            regexPatternMap.put(route, Pattern.compile(regexStr.toString()));
        }

        Pattern pattern = regexPatternMap.get(route);
        return pattern.matcher(urlPath);
    }

    private boolean enrichSpan(Span<?> span, String method, String route, String urlPath) {
        if (route.contains("{")) {
            Matcher matcher = matchUrl(route, urlPath);
            if (!matcher.find()) {
                return false;
            }
            setTarget(span, matcher, route);
            setDocId(span, matcher, route);
        } else if (!route.equals(urlPath)) {
            return false;
        }
        StringBuilder name = span.getAndOverrideName(AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK);
        if (name != null) {
            name.append("Elasticsearch: ").append(method).append(" ").append(route);
        }
        return true;
    }

    private void setTarget(Span<?> span, Matcher matcher, String route) {
        try {
            if (route.contains("{index}")) {
                String target = matcher.group("index");
                span.withOtelAttribute("db.elasticsearch.target", target);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void setDocId(Span<?> span, Matcher matcher, String route) {
        try {
            if (route.startsWith("/{index}/_") && route.endsWith("/{id}")) {
                String docId = matcher.group("id");
                span.withOtelAttribute("db.elasticsearch.doc_id", docId);
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
