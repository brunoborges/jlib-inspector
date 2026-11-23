# Prompt para gerar um projeto de monitoramento de dependências Java

```text
Você é um arquiteto de software e engenheiro sênior especializado em Java, Java Agents, instrumentação da JVM, observabilidade, segurança de software, APIs e sistemas distribuídos.

Sua tarefa é projetar e implementar do zero um projeto open source funcional para inventariar, em tempo de execução, as bibliotecas usadas por aplicações Java.

Não entregue apenas uma descrição ou exemplos isolados. Crie a estrutura completa do repositório, implemente o código, escreva testes, execute as verificações disponíveis e documente como usar a solução.

## 1. Visão do produto

O sistema deve responder com evidências a perguntas como:

- Quais arquivos JAR estão presentes em uma aplicação Java em execução?
- Quais desses JARs efetivamente forneceram classes para a JVM?
- Qual versão de cada componente está em uso?
- Quais aplicações usam determinado componente ou checksum?
- O que mudou no inventário entre dois deploys?
- Existem JARs com o mesmo nome e versão, mas checksums diferentes?
- Quais componentes estão declarados, mas ainda não foram carregados?

O produto não deve se apresentar como um APM completo. Seu foco é inventário de componentes Java em runtime, rastreabilidade e apoio a investigações de supply chain.

## 2. Arquitetura obrigatória

Implemente um monorepo com os seguintes módulos:

```text
runtime-java-inventory/
├── agent/                 # Java Agent carregado com -javaagent
├── common/                # Contratos, DTOs e utilitários compartilhados
├── collector/             # API de ingestão e consulta
├── dashboard/             # Interface web
├── sample-app/            # Aplicação Java de demonstração
├── integration-tests/     # Testes entre agente, collector e sample
├── docker/                # Imagens e ambiente local
├── docs/                  # Arquitetura, protocolo e operação
├── pom.xml
├── docker-compose.yml
├── README.md
└── LICENSE
```

Use:

- Java 21 ou superior;
- Maven Wrapper;
- build Maven multi-módulo;
- JUnit 5;
- uma API HTTP com framework Java maduro e adequado;
- PostgreSQL para persistência;
- migrations de banco versionadas;
- frontend web simples e responsivo;
- Docker Compose para ambiente local;
- GitHub Actions ou CI equivalente.

Escolha nomes definitivos para o projeto, pacotes e artefatos. Use um namespace neutro que não imite projetos existentes.

## 3. Java Agent

O módulo `agent` deve produzir um shaded JAR autocontido com `Premain-Class` e ser iniciado assim:

```bash
java "-javaagent:runtime-inventory-agent.jar=collector:http://localhost:8080,interval:30s" -jar application.jar
```

O agente deve:

1. Ser carregado por `premain`.
2. Usar `Instrumentation` e `ClassFileTransformer`.
3. Nunca modificar o bytecode das classes; o transformer deve retornar `null`.
4. Examinar classes que já estavam carregadas quando o agente iniciou.
5. Observar novas classes carregadas.
6. Obter a origem por `ProtectionDomain` e `CodeSource`.
7. Examinar `java.class.path`.
8. Examinar URLs acessíveis dos classloaders quando aplicável.
9. Identificar arquivos JAR de primeiro nível.
10. Identificar JARs aninhados em fat JARs, incluindo estruturas Spring Boot.
11. Registrar separadamente componentes declarados e componentes carregados.
12. Calcular SHA-256 quando possível.
13. Ler `META-INF/MANIFEST.MF`.
14. Procurar `META-INF/maven/**/pom.properties` para obter `groupId`, `artifactId` e `version`.
15. Produzir Package URL quando houver coordenadas suficientes.
16. Registrar tamanho, caminho normalizado e tipo de arquivo.
17. Suportar `.jar`, `.war` e `.ear` quando tecnicamente possível.
18. Funcionar com caminhos Unix e Windows.
19. Tratar espaços e caracteres especiais em URLs.
20. Operar de forma fail-open: uma falha do agente não deve derrubar a aplicação.

Cada registro de componente deve distinguir claramente:

```text
declared: encontrado no classpath ou dentro de um arquivo
loaded: pelo menos uma classe foi associada a essa origem
nested: componente localizado dentro de outro arquivo
```

`loaded=true` não deve ser descrito como prova de que uma funcionalidade vulnerável foi executada. Significa apenas que ao menos uma classe foi carregada daquela origem.

### 3.1 Desempenho do agente

O agente executa dentro da aplicação monitorada e deve ter overhead baixo.

Diretrizes obrigatórias:

- O transformer deve executar o mínimo possível.
- Não leia um JAR inteiro a cada classe carregada.
- Não calcule repetidamente o mesmo checksum.
- Deduplicate origens antes de fazer trabalho pesado.
- Não mantenha referências fortes desnecessárias a classloaders.
- Se classloaders forem acompanhados, deduplicate por identidade e use referências fracas corretamente.
- Não percorra uma coleção crescente de classloaders a cada classe.
- Use threads daemon para trabalho auxiliar.
- Use filas limitadas.
- Não crie uma thread por evento.
- Implemente limites configuráveis para varredura de arquivos grandes.
- Meça e documente tempo de inicialização, memória adicional e custo do transformer.

### 3.2 Segurança e privacidade do agente

Por padrão, o agente não deve enviar:

- valores de variáveis de ambiente;
- todas as system properties;
- tokens;
- senhas;
- cookies;
- headers de autenticação;
- chaves privadas;
- linhas de comando sem redaction;
- payloads arbitrários da aplicação.

Implemente redaction centralizada para argumentos e configurações com nomes contendo, sem diferenciar maiúsculas de minúsculas:

```text
password, passwd, pwd, secret, token, credential, api_key, apikey,
private_key, access_key, client_secret, authorization, cookie
```

Cubra formatos como:

```text
--password=secret
--password secret
-Dpassword=secret
https://user:password@example.com
```

Não registre payloads completos em logs. O agente deve poder usar credenciais de transporte internamente sem incluí-las no inventário.

### 3.3 Configuração do agente

Suporte, no mínimo:

```text
collector:http://host:port
interval:30s
interval:5m
interval:1h
timeout:10s
hash:true|false
scanNested:true|false
maxArchiveSize:300m
```

Também suporte configuração equivalente por variáveis de ambiente com precedência documentada.

O agente deve:

- produzir um snapshot inicial;
- enviar deltas depois do snapshot inicial;
- fazer um envio final no shutdown;
- usar timeouts;
- aplicar retry com exponential backoff e jitter;
- limitar a fila de eventos;
- descartar de forma controlada e contabilizada quando o limite for atingido;
- não impedir o encerramento da JVM;
- expor logs concisos sobre sucesso, falha, retry e descarte.

## 4. Protocolo de ingestão

Crie e documente um protocolo JSON versionado.

Todo payload deve conter:

```json
{
  "schemaVersion": 1,
  "messageId": "uuid",
  "messageType": "SNAPSHOT ou DELTA",
  "observedAt": "timestamp UTC",
  "service": {},
  "deployment": {},
  "instance": {},
  "runtime": {},
  "components": []
}
```

Modele separadamente:

- serviço lógico;
- ambiente;
- versão ou deployment;
- instância da JVM;
- snapshot;
- componente;
- ocorrência do componente em uma instância.

Use identificadores estáveis e não misture identidade do serviço com identidade de processo.

Considere atributos como:

- `service.name`;
- `service.version`;
- `deployment.environment`;
- commit SHA;
- build ID;
- hostname;
- container ID;
- pod e namespace quando disponíveis por configuração explícita;
- PID;
- horário de início da JVM;
- versão e fornecedor do Java.

O protocolo deve suportar:

- idempotência por `messageId`;
- snapshots completos;
- deltas;
- compressão HTTP;
- evolução de schema;
- rejeição clara de versões incompatíveis;
- limites de payload;
- respostas de erro estruturadas.

## 5. Collector

O módulo `collector` deve:

- receber inventários do agente;
- autenticar agentes;
- validar schema e tamanho do payload;
- persistir os dados no PostgreSQL;
- evitar duplicação por `messageId`;
- manter histórico de snapshots;
- registrar primeira e última observação;
- identificar instâncias ativas e inativas;
- permitir retenção configurável;
- oferecer paginação e filtros;
- produzir respostas de erro consistentes;
- ter health checks de liveness e readiness;
- expor métricas operacionais;
- usar pool de conexões e limites de threads;
- realizar shutdown gracioso.

Implemente autenticação separada para:

- ingestão por agentes;
- leitura da API;
- operações administrativas.

Para o ambiente local, permita credenciais simples documentadas. A arquitetura deve permitir integração futura com OIDC.

Não registre tokens, payloads completos ou dados redigidos em logs.

### 5.1 API mínima

Implemente e documente endpoints equivalentes a:

```text
POST /api/v1/ingest
GET  /api/v1/services
GET  /api/v1/services/{serviceId}
GET  /api/v1/services/{serviceId}/deployments
GET  /api/v1/instances
GET  /api/v1/instances/{instanceId}
GET  /api/v1/instances/{instanceId}/components
GET  /api/v1/components
GET  /api/v1/components/{componentId}
GET  /api/v1/components/{componentId}/usages
GET  /api/v1/snapshots/{snapshotId}
GET  /api/v1/compare?before={id}&after={id}
GET  /api/v1/health/live
GET  /api/v1/health/ready
```

Filtros importantes:

- serviço;
- ambiente;
- versão;
- grupo e artefato;
- Package URL;
- checksum;
- nome do arquivo;
- `loaded`;
- `declared`;
- `nested`;
- primeira e última observação.

## 6. Persistência

Modele no banco, no mínimo:

- services;
- deployments;
- runtime_instances;
- snapshots;
- components;
- component_occurrences;
- ingest_messages;
- agent_credentials ou mecanismo equivalente;
- audit_events.

Requisitos:

- Checksums conhecidos devem ser usados para deduplicar componentes.
- Ausência de checksum não pode causar colisão silenciosa.
- Caminho de arquivo pertence à ocorrência, não necessariamente ao componente global.
- `loaded` deve ser armazenado por snapshot ou ocorrência.
- Metadados devem indicar sua fonte e grau de confiança quando houver inferência.
- Migrations devem ser reproduzíveis e testadas.
- Crie índices para as buscas principais.

## 7. Dashboard

Crie uma interface web simples, acessível e responsiva com:

- visão geral de serviços;
- instâncias ativas e inativas;
- inventário por serviço, deployment e instância;
- filtros de JARs carregados, não carregados e aninhados;
- busca por nome, coordenadas, Package URL e checksum;
- detalhes do componente;
- lista de aplicações que usam o componente;
- comparação entre snapshots;
- destaque para JAR adicionado, removido ou alterado;
- cópia segura de identificadores;
- estados de loading, vazio e erro;
- paginação;
- indicação clara do horário da última observação.

Não exponha secrets, valores de ambiente ou linhas de comando sem redaction.


## 8. OpenTelemetry e integrações

Implemente uma integração opcional com OpenTelemetry sem tornar o SDK uma dependência obrigatória da aplicação monitorada.

Documente:

- atributos de recurso;
- atributos de componente;
- endpoint OTLP;
- headers de autenticação;
- timeouts;
- comportamento em falha.

Nunca inclua headers ou credenciais nos eventos exportados.

Prepare o modelo para exportação futura de SBOM CycloneDX ou SPDX. Se implementar SBOM no primeiro release, mantenha-o como funcionalidade do collector, não como processamento pesado dentro do transformer.

Não implemente conclusões automáticas sobre vulnerabilidades sem uma fonte determinística. Se houver integração de vulnerabilidades, diferencie:

- componente identificado;
- intervalo de versão afetado;
- componente carregado;
- confirmação ou incerteza da correspondência;
- uso de uma classe versus execução de um caminho vulnerável.

## 9. Observabilidade do próprio sistema

O agente e o collector devem possuir observabilidade própria.

Métricas recomendadas:

- arquivos descobertos;
- origens de classe observadas;
- componentes carregados;
- tempo de varredura;
- tempo de hashing;
- tamanho do snapshot;
- deltas gerados;
- envios bem-sucedidos;
- falhas e retries;
- eventos descartados;
- mensagens ingeridas;
- mensagens rejeitadas;
- latência de ingestão;
- tamanho das filas;
- tempo de consultas.

Use logs estruturados no collector. No agente, mantenha logs compactos para reduzir interferência na aplicação.

## 10. Testes obrigatórios

Implemente testes para, no mínimo:

### Agent

- parsing de argumentos;
- precedência de configuração;
- inventário de classpath;
- JAR normal;
- fat JAR;
- JAR aninhado;
- manifest ausente;
- `pom.properties` ausente;
- checksum;
- normalização de caminhos;
- espaços no caminho;
- `ProtectionDomain` nulo;
- `CodeSource` nulo;
- classloader customizado;
- concorrência;
- deduplicação de classloaders e origens;
- redaction de secrets;
- snapshot e delta;
- timeout;
- retry;
- fila cheia;
- shutdown;
- indisponibilidade do collector;
- garantia de que o transformer retorna `null`.

### Collector

- autenticação;
- autorização;
- schema inválido;
- payload grande demais;
- idempotência;
- snapshot;
- delta;
- concorrência;
- persistência;
- migrations;
- filtros;
- paginação;
- comparação de snapshots;
- retenção;
- redaction de logs.

### Integração

- iniciar PostgreSQL e collector;
- executar a sample application com `-javaagent`;
- confirmar ingestão;
- confirmar ao menos um componente declarado;
- confirmar ao menos um componente carregado;
- confirmar descoberta de JAR aninhado;
- confirmar envio periódico;
- confirmar envio no shutdown;
- consultar o resultado pela API;
- comparar dois snapshots.

Os testes E2E devem falhar quando essas condições não forem atendidas. Não use `|| true` para esconder falhas.

## 11. CI e qualidade

Configure CI para:

- compilar todos os módulos;
- executar testes unitários;
- executar testes de integração;
- construir o frontend;
- executar lint do frontend;
- verificar formatação;
- executar análise estática;
- produzir relatório de cobertura;
- executar scanner de dependências;
- construir imagens Docker;
- validar migrations;
- publicar artefatos de teste.

Não use nomes de artefato com versão hardcoded no pipeline. Descubra a versão pelo build ou use padrões controlados.

Falhas de testes, lint, build, integração ou segurança acima da política definida devem falhar o pipeline.

## 12. Docker e execução local

O `docker-compose.yml` deve iniciar:

- PostgreSQL;
- collector;
- dashboard;
- sample application instrumentada.

Adicione health checks e dependências baseadas em saúde, não apenas ordem de inicialização.

Use imagens pequenas, usuários não root e builds multi-stage.

Forneça um caminho de demonstração com poucos comandos:

```bash
./mvnw clean verify
docker compose up --build
```

O README deve indicar exatamente onde abrir o dashboard e como verificar a API.

## 13. Documentação

Crie documentação para:

- arquitetura;
- modelo de dados;
- protocolo de ingestão;
- configuração do agente;
- segurança e threat model;
- política de redaction;
- operação;
- troubleshooting;
- limites conhecidos;
- desenvolvimento local;
- testes;
- processo de release;
- compatibilidade de Java;
- overhead medido.

Inclua diagramas Mermaid quando ajudarem a explicar fluxo, sequência ou modelo.

Documente explicitamente:

- que o sistema observa origens de classes, não execução de métodos;
- que `loaded=false` não prova que o componente nunca será carregado;
- que `loaded=true` não prova exploração de vulnerabilidade;
- que componentes carregados antes do agente podem ter limitações dependendo do modo de inicialização;
- como secrets são protegidos;
- quais dados saem da JVM.

## 14. Restrições de implementação

- Não invente resultados de testes ou benchmarks.
- Não declare produção-ready sem evidência.
- Não deixe TODOs em funcionalidades essenciais.
- Não armazene credenciais no repositório.
- Não exponha secrets em logs, respostas ou screenshots.
- Não use coleções ilimitadas em caminhos de ingestão.
- Não execute I/O de rede dentro do callback do transformer.
- Não use dependências da aplicação monitorada; o agente deve isolar suas dependências no shaded JAR.
- Não altere o comportamento da aplicação monitorada.
- Não faça o agente encerrar a JVM em caso de falha.

## 15. Sequência de trabalho

Trabalhe incrementalmente:

1. Defina arquitetura, contratos e ameaças.
2. Crie o build multi-módulo.
3. Implemente o modelo compartilhado e o protocolo.
4. Implemente o núcleo do agente com testes.
5. Implemente ingestão e persistência.
6. Implemente APIs de consulta.
7. Crie sample application e testes de integração.
8. Implemente o dashboard.
9. Adicione Docker Compose.
10. Configure CI e verificadores.
11. Meça overhead.
12. Revise segurança, logs e redaction.
13. Execute o build e todos os testes.
14. Corrija falhas encontradas.
15. Finalize a documentação.

Antes de cada grupo de mudanças, explique brevemente o objetivo. Não pare apenas porque uma etapa é extensa; entregue o máximo funcional possível e identifique claramente qualquer limitação real.

## 16. Critérios de aceite

O projeto só pode ser considerado concluído quando:

- o Maven Wrapper compilar todos os módulos;
- os testes unitários passarem;
- os testes de integração passarem;
- o shaded agent JAR contiver `Premain-Class`;
- a sample application iniciar com o agente;
- o collector receber e persistir um inventário;
- a API retornar os componentes observados;
- o dashboard exibir os dados;
- um JAR carregado for identificado como carregado;
- um JAR aninhado for identificado;
- snapshots puderem ser comparados;
- indisponibilidade do collector não impedir a aplicação de iniciar;
- secrets usados nos testes não aparecerem no payload ou nos logs;
- o Docker Compose iniciar o ambiente completo;
- a documentação permitir reproduzir a demonstração do zero.

## 17. Entrega final esperada

Ao terminar, apresente:

1. Visão geral da arquitetura implementada.
2. Árvore dos principais arquivos.
3. Decisões de projeto e trade-offs.
4. Controles de segurança implementados.
5. Estratégia de desempenho do agente.
6. Protocolo de ingestão.
7. Modelo de persistência.
8. Comandos para build, teste e execução.
9. Resultado real dos testes executados.
10. Resultado real dos testes de integração.
11. Overhead medido, se o ambiente permitir medição confiável.
12. Limitações restantes.
13. Próximos passos recomendados.

Comece inspecionando o ambiente de trabalho, confirme que não sobrescreverá arquivos do usuário e então construa o projeto de forma incremental e verificável.
```
