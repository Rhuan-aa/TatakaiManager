package com.tatakai.manager.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.util.List;

/**
 * Atualiza o schema sozinho a cada boot, antes do Hibernate (que em produção roda com {@code validate} e não
 * cria nada): {@code db/upgrade.sql} é idempotente e cria tabelas/colunas que faltam. Assim o deploy só precisa
 * subir a versão nova. Desligar: {@code APP_SCHEMA_UPGRADE=false}. Regenerar: {@code scripts/gen_upgrade_sql.py}.
 */
@Configuration
public class SchemaUpgradeConfig {

    @Bean
    public DataSourceScriptDatabaseInitializer schemaUpgrade(DataSource dataSource,
                                                             @Value("${app.schema-upgrade:true}") boolean enabled) {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of("classpath:db/upgrade.sql"));
        settings.setMode(enabled ? DatabaseInitializationMode.ALWAYS : DatabaseInitializationMode.NEVER);
        // o script tem blocos DO $$ … $$ com ';' dentro: vai inteiro num só comando (o driver do Postgres separa)
        settings.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        settings.setContinueOnError(false);
        return new DataSourceScriptDatabaseInitializer(dataSource, settings);
    }
}
