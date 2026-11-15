package com.aicontractmanager.integration;

import com.aicontractmanager.auth.domain.AuthUser;
import com.aicontractmanager.auth.dto.OAuthProvider;
import com.aicontractmanager.auth.service.JwtTokenProvider;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"server.address=127.0.0.1","app.push.fcm.enabled=false","app.ai.enabled=false"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="mysql.integration",matches="true")
class BackendMysqlTest {
 static final String schema="acm_verify_"+UUID.randomUUID().toString().replace("-", "");
 static String adminUrl,user,password;
 static boolean created;
 @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
  var yaml=new YamlPropertiesFactoryBean();
  yaml.setResources(new FileSystemResource("src/main/resources/application.yml"),new FileSystemResource("src/main/resources/application-local.yml"));
  var env=new StandardEnvironment();env.getPropertySources().addLast(new PropertiesPropertySource("local",yaml.getObject()));
  String configured=env.getProperty("spring.datasource.url");
  assertTrue(configured.startsWith("jdbc:mysql://localhost:")||configured.startsWith("jdbc:mysql://127.0.0.1:"),"Only local MySQL may be used");
  int slash=configured.indexOf('/',"jdbc:mysql://".length());
  String host=configured.substring(0,slash);
  adminUrl=host+"/?serverTimezone=Asia/Seoul";
  user=env.getProperty("spring.datasource.username");password=env.getProperty("spring.datasource.password");
  try(var c=DriverManager.getConnection(adminUrl,user,password);var s=c.createStatement()) {
   s.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4");created=true;
  }
  String url=host+"/"+schema+"?serverTimezone=Asia/Seoul";
  // Exercise a V3 -> V4 upgrade with an existing detection row.
  Flyway.configure().dataSource(url,user,password).target("3").load().migrate();
  try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {
   s.execute("INSERT INTO users(id,email,name) VALUES('00000000-0000-0000-0000-000000000001','legacy@example.invalid','legacy')");
   s.execute("INSERT INTO payment_detection_events(id,user_id,merchant_name,amount,paid_at,source_package) VALUES('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','legacy',100,NOW(),'test')");
  }
  registry.add("spring.datasource.url",()->url);registry.add("spring.datasource.username",()->user);registry.add("spring.datasource.password",()->password);
 }
 @AfterAll static void cleanup() throws Exception {
  if(created) try(var c=DriverManager.getConnection(adminUrl,user,password);var s=c.createStatement()) {s.execute("DROP DATABASE "+schema);
   try(var rows=s.executeQuery("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+schema+"'")) {rows.next();assertEquals(0,rows.getInt(1));}}
 }
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 @Autowired com.aicontractmanager.notification.repository.NotificationRepository notifications;
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper mapper; @Autowired JwtTokenProvider jwt;
 String token,other; UUID owner;
 @BeforeEach void users() {owner=UUID.randomUUID(); token=addUser(owner);other=addUser(UUID.randomUUID());}
 String addUser(UUID id) {
  String email=id+"@example.invalid";
  jdbc.update("INSERT INTO users(id,email,name) VALUES(?,?,?)",id.toString(),email,"검증 사용자");
  return jwt.issue(new AuthUser(id,email,"검증 사용자",OAuthProvider.KAKAO,false));
 }
 JsonNode call(String method,String url,String auth,Object body,int expected) throws Exception {
  var b=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),url);
  if(auth!=null)b.header("Authorization","Bearer "+auth);
  if(body!=null)b.contentType("application/json").content(mapper.writeValueAsBytes(body));
  var response=mvc.perform(b).andReturn().getResponse();
  assertEquals(expected,response.getStatus(),method+" "+url+" "+response.getContentAsString());
  return response.getContentAsByteArray().length==0?mapper.nullNode():mapper.readTree(response.getContentAsByteArray());
 }
 ObjectNode contract(int amount,LocalDate end) {var n=mapper.createObjectNode().put("title","검증 계약").put("category","FITNESS").put("monthlyAmount",amount);if(end!=null)n.put("contractEndDate",end.toString());return n;}
 String create(ObjectNode body) throws Exception {return call("POST","/api/aicontract/contracts",token,body,201).get("id").asText();}
 int count(String sql,Object... args){return jdbc.queryForObject(sql,Integer.class,args);}
 ObjectNode payment(String key,LocalDateTime time) {return mapper.createObjectNode().put("eventId",key).put("merchantName","검증 헬스장").put("amount",30000).put("paidAt",time.toString()).put("sourcePackage","test.bank");}
 @Test void migrationsPreserveExistingRows() {
  assertEquals(4,count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL"));
  assertEquals(1,count("SELECT COUNT(*) FROM payment_detection_events WHERE id='00000000-0000-0000-0000-000000000002' AND client_event_id IS NULL"));
 }
 @Test void paymentDedupAndSuggestionAcceptance() throws Exception {
  var old=payment("a",LocalDateTime.now().minusDays(31));var next=payment("b",LocalDateTime.now().minusDays(1));
  call("POST","/api/aicontract/payment-detections",token,old,200);
  var result=call("POST","/api/aicontract/payment-detections",token,next,200);String suggestion=result.get("suggestionId").asText();assertNotEquals("null",suggestion);
  assertTrue(call("POST","/api/aicontract/payment-detections",token,next,200).get("duplicate").asBoolean());
  assertEquals(2,count("SELECT COUNT(*) FROM payment_transactions WHERE user_id=?",owner.toString()));
  assertEquals(1,count("SELECT COUNT(*) FROM notifications WHERE suggestion_id=?",suggestion));
  call("POST","/api/aicontract/subscription-suggestions/"+suggestion+"/accept",other,null,404);
  call("POST","/api/aicontract/subscription-suggestions/"+suggestion+"/accept",token,null,200);
  call("POST","/api/aicontract/subscription-suggestions/"+suggestion+"/accept",token,null,409);
  String id=create(contract(30000,null));
  call("POST","/api/aicontract/subscription-suggestions/"+suggestion+"/complete",token,Map.of("contractId",id),204);
  assertEquals(id,jdbc.queryForObject("SELECT contract_id FROM contract_suggestions WHERE id=?",String.class,suggestion));
 }
 @Test void validatesInputAndAuthentication() throws Exception {
  call("POST","/api/aicontract/payment-detections",null,payment("x",LocalDateTime.now().minusDays(1)),403);
  call("POST","/api/aicontract/payment-detections",token,payment("x",LocalDateTime.now().plusDays(1)),400);
  call("POST","/api/aicontract/payment-detections",token,payment("x",LocalDateTime.now().minusDays(1)).put("amount",-1),400);
  assertEquals(0,count("SELECT COUNT(*) FROM payment_detection_events WHERE user_id=?",owner.toString()));
 }
 @Test void rejectsOtherOwnersAndSorts() throws Exception {
  String cheap=create(contract(100,LocalDate.now().plusDays(5)));String expensive=create(contract(200,LocalDate.now().plusDays(10)));
  assertEquals(expensive,call("GET","/api/aicontract/contracts?sort=amount",token,null,200).get(0).get("id").asText());
  assertEquals(cheap,call("GET","/api/aicontract/contracts?sort=deadline",token,null,200).get(0).get("id").asText());
  call("GET","/api/aicontract/contracts?sort=invalid",token,null,400);
  call("GET","/api/aicontract/contracts/"+cheap,other,null,404);
  call("POST","/api/aicontract/contracts/"+cheap+"/cancel",other,null,404);
  call("DELETE","/api/aicontract/contracts/"+cheap,other,null,404);
 }
 @Test void updatesAndCancelsManagedSchedules() throws Exception {
  var body=contract(100,LocalDate.now().plusDays(10));String id=create(body);
  call("PUT","/api/aicontract/contracts/"+id,token,body,200);
  assertEquals(1,count("SELECT COUNT(*) FROM contract_schedules WHERE contract_id=?",id));
  body.put("contractEndDate",LocalDate.now().plusDays(20).toString());call("PUT","/api/aicontract/contracts/"+id,token,body,200);
  assertEquals(1,count("SELECT COUNT(*) FROM contract_schedules WHERE contract_id=? AND status='PENDING'",id));
  assertEquals(1,count("SELECT COUNT(*) FROM notifications WHERE contract_id=? AND dispatch_status='CANCELLED'",id));
  call("POST","/api/aicontract/contracts/"+id+"/cancel",token,null,200);
  assertEquals(0,count("SELECT COUNT(*) FROM contract_schedules WHERE contract_id=? AND status='PENDING'",id));
  assertEquals(0,count("SELECT COUNT(*) FROM notifications WHERE contract_id=? AND dispatch_status='PENDING'",id));
  assertEquals("CANCELLED",call("POST","/api/aicontract/contracts/"+id+"/verify-amount",token,Map.of("monthlyAmount",100),200).get("status").asText());
 }
 @Test void removingAndRestoringDateRecreatesReminder() throws Exception {
  var body=contract(100,LocalDate.now().plusDays(10));String id=create(body);
  call("PUT","/api/aicontract/contracts/"+id,token,contract(100,null),200);
  call("PUT","/api/aicontract/contracts/"+id,token,body,200);
  assertEquals(1,count("SELECT COUNT(*) FROM contract_schedules WHERE contract_id=? AND status='PENDING'",id));
 }
 @Test void calendarHasNoDuplicateManagedEvent() throws Exception {
  var end=LocalDate.now().plusDays(1);String id=create(contract(100,end));
  call("POST","/api/aicontract/contracts/"+id+"/verify-amount",token,Map.of("monthlyAmount",100),200);
  var dashboard=call("GET","/api/aicontract/dashboard?month="+YearMonth.from(end),token,null,200);
  assertEquals(1,mapper.readTree(dashboard.toString()).get("calendarEvents").size());
 }
 @Test void dueQueueReadAndRetryUseRealSql() throws Exception {
  String due=create(contract(100,LocalDate.now().plusDays(1)));
  String future=create(contract(200,LocalDate.now().plusDays(10)));
  UUID dueId=UUID.fromString(jdbc.queryForObject("SELECT id FROM notifications WHERE contract_id=?",String.class,due));
  UUID futureId=UUID.fromString(jdbc.queryForObject("SELECT id FROM notifications WHERE contract_id=?",String.class,future));
  var visible=call("GET","/api/aicontract/notifications",token,null,200);
  assertEquals(1,visible.size());assertEquals(dueId.toString(),visible.get(0).get("id").asText());
  call("POST","/api/aicontract/notifications/read-all",token,null,204);
  assertEquals(1,count("SELECT COUNT(*) FROM notifications WHERE user_id=? AND read_at IS NOT NULL",owner.toString()));
  var claimed=notifications.claimDue(100);
  assertTrue(claimed.stream().anyMatch(n->n.id().equals(dueId)));
  assertFalse(claimed.stream().anyMatch(n->n.id().equals(futureId)));
  assertFalse(notifications.claimDue(100).stream().anyMatch(n->n.id().equals(dueId)));
  notifications.markPushFailed(dueId,"simulated provider failure",1);
  assertEquals("PENDING",jdbc.queryForObject("SELECT dispatch_status FROM notifications WHERE id=?",String.class,dueId.toString()));
  assertFalse(notifications.claimDue(100).stream().anyMatch(n->n.id().equals(dueId)));
  jdbc.update("UPDATE notifications SET dispatch_next_attempt_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=?",dueId.toString());
  assertTrue(notifications.claimDue(100).stream().anyMatch(n->n.id().equals(dueId)));
  notifications.markPushSent(dueId);
  assertEquals(1,count("SELECT COUNT(*) FROM notifications WHERE id=? AND dispatch_status='SENT' AND sent_at IS NOT NULL",dueId.toString()));
 }
 @Test void actualHttpPortChecksAuthentication() throws Exception {
  var client=java.net.http.HttpClient.newHttpClient();
  var uri=java.net.URI.create("http://127.0.0.1:"+port+"/api/aicontract/contracts");
  assertEquals(403,client.send(java.net.http.HttpRequest.newBuilder(uri).GET().build(),java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
  assertEquals(200,client.send(java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
 }
 @Test void changingDateBackAndForthDoesNotDuplicateAndPreservesManualSchedules() throws Exception {
  var body=contract(100,LocalDate.now().plusDays(10));String id=create(body);
  call("POST","/api/aicontract/contracts/"+id+"/schedules",token,Map.of("scheduleType","CUSTOM","dueDate",LocalDate.now().plusDays(2).toString(),"actionTitle","직접 등록","reminderDaysBefore",0),201);
  body.put("contractEndDate",LocalDate.now().plusDays(20).toString());call("PUT","/api/aicontract/contracts/"+id,token,body,200);
  body.put("contractEndDate",LocalDate.now().plusDays(10).toString());call("PUT","/api/aicontract/contracts/"+id,token,body,200);
  call("PUT","/api/aicontract/contracts/"+id,token,body,200);
  assertEquals(1,count("SELECT COUNT(*) FROM contract_schedules WHERE contract_id=? AND managed=TRUE AND status='PENDING'",id));
  assertEquals(1,count("SELECT COUNT(*) FROM contract_schedules WHERE contract_id=? AND schedule_type='CUSTOM' AND status='PENDING'",id));
 }
 @Test void scheduleCompletionCancelsReminderAndIsIdempotencyGuarded() throws Exception {
  String id=create(contract(100,LocalDate.now().plusDays(2)));
  String schedule=jdbc.queryForObject("SELECT id FROM contract_schedules WHERE contract_id=?",String.class,id);
  call("POST","/api/aicontract/schedules/"+schedule+"/complete",other,null,404);
  call("POST","/api/aicontract/schedules/"+schedule+"/complete",token,null,200);
  call("POST","/api/aicontract/schedules/"+schedule+"/complete",token,null,409);
  call("PUT","/api/aicontract/contracts/"+id,token,contract(100,LocalDate.now().plusDays(2)),200);
  assertEquals(0,count("SELECT COUNT(*) FROM notifications WHERE contract_id=? AND dispatch_status='PENDING'",id));
 }
 @Test void concurrentPaymentRetriesPersistOnlyOnce() throws Exception {
  var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
  var body=payment("concurrent",LocalDateTime.now().minusDays(1));
  try {
   var first=executor.submit(()->call("POST","/api/aicontract/payment-detections",token,body,200));
   var second=executor.submit(()->call("POST","/api/aicontract/payment-detections",token,body,200));
   assertNotEquals(first.get().get("duplicate").asBoolean(),second.get().get("duplicate").asBoolean());
   assertEquals(1,count("SELECT COUNT(*) FROM payment_transactions WHERE user_id=?",owner.toString()));
  } finally {executor.shutdownNow();}
 }
 @Test void noProposalForWeeklyPaymentsOrRejectedMerchant() throws Exception {
  call("POST","/api/aicontract/payment-detections",token,payment("a",LocalDateTime.now().minusDays(70)),200);
  assertTrue(call("POST","/api/aicontract/payment-detections",token,payment("b",LocalDateTime.now().minusDays(63)),200).get("suggestionId").isNull());
  var result=call("POST","/api/aicontract/payment-detections",token,payment("c",LocalDateTime.now().minusDays(33)),200);
  String suggestion=result.get("suggestionId").asText();
  call("POST","/api/aicontract/subscription-suggestions/"+suggestion+"/reject",token,null,204);
  assertTrue(call("POST","/api/aicontract/payment-detections",token,payment("d",LocalDateTime.now().minusDays(3)),200).get("suggestionId").isNull());
  assertEquals(1,count("SELECT COUNT(*) FROM contract_suggestions WHERE user_id=?",owner.toString()));
 }
 @Test void deletionCancelsRemindersAndHidesContract() throws Exception {
  String id=create(contract(100,LocalDate.now().plusDays(10)));
  call("DELETE","/api/aicontract/contracts/"+id,token,null,204);
  call("GET","/api/aicontract/contracts/"+id,token,null,404);
  assertEquals(0,count("SELECT COUNT(*) FROM notifications WHERE contract_id=? AND dispatch_status='PENDING'",id));
 }
}
