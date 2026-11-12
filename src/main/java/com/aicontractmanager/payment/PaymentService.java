package com.aicontractmanager.payment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
@Service
public class PaymentService {
 private final JdbcTemplate jdbc;
 public PaymentService(JdbcTemplate jdbc) {this.jdbc=jdbc;}
 public record Result(UUID detectionId, UUID suggestionId, boolean duplicate) {}
 @Transactional
 public Result receive(UUID userId, PaymentRequest request) {
  String user=userId.toString();
  // Serialize detection for one user, including duplicate retries and concurrent proposals.
  var users=jdbc.queryForList("SELECT id FROM users WHERE id=? AND deleted_at IS NULL FOR UPDATE",String.class,user);
  if(users.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"사용자를 찾을 수 없습니다.");
  var old=jdbc.queryForList("SELECT id FROM payment_detection_events WHERE user_id=? AND client_event_id=?",String.class,user,request.eventId());
  if(!old.isEmpty()) return new Result(UUID.fromString(old.get(0)),null,true);
  UUID id=UUID.randomUUID();
  String merchant=request.merchantName().trim();
  jdbc.update("INSERT INTO payment_detection_events(id,user_id,merchant_name,amount,paid_at,source_package,parser_version,client_event_id) VALUES(?,?,?,?,?,?,?,?)",id.toString(),user,merchant,request.amount(),request.paidDate(),request.sourcePackage(),request.parserVersion(),request.eventId());
  jdbc.update("INSERT INTO payment_transactions(id,user_id,detection_event_id,merchant_name,amount,paid_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),user,id.toString(),merchant,request.amount(),request.paidDate());
  var dates=jdbc.query("SELECT paid_at FROM payment_detection_events WHERE user_id=? AND merchant_name=? AND amount=? ORDER BY paid_at DESC LIMIT 2",(rs,n)->rs.getObject(1,LocalDate.class),user,merchant,request.amount());
  if(dates.size()<2 || !monthly(dates.get(1),dates.get(0))) return new Result(id,null,false);
  // A previously rejected or accepted proposal must not be regenerated on every payment.
  var previous=jdbc.queryForList("SELECT id FROM contract_suggestions WHERE user_id=? AND merchant_name=? AND status IN ('PENDING','ACCEPTED','REJECTED','IGNORED') LIMIT 1",String.class,user,merchant);
  if(!previous.isEmpty()) return new Result(id,null,false);
  Integer active=jdbc.queryForObject("SELECT COUNT(*) FROM contracts WHERE user_id=? AND provider_name=? AND deleted_at IS NULL AND status='ACTIVE'",Integer.class,user,merchant);
  if(active!=null && active>0) return new Result(id,null,false);
  UUID suggestion=UUID.randomUUID();
  jdbc.update("INSERT INTO contract_suggestions(id,user_id,merchant_name,estimated_amount,estimated_cycle,detection_count) VALUES(?,?,?,?,'MONTHLY',2)",suggestion.toString(),user,merchant,request.amount());
  jdbc.update("INSERT INTO notifications(id,user_id,suggestion_id,notification_type,title,body,scheduled_at) VALUES(?,?,?,'SUBSCRIPTION_SUGGESTION',?,?,CURRENT_TIMESTAMP)",UUID.randomUUID().toString(),user,suggestion.toString(),"정기결제 확인 제안",merchant+"의 반복 결제가 발견됐어요. 구독으로 등록하시겠어요?");
  return new Result(id,suggestion,false);
 }
 static boolean monthly(LocalDate earlier, LocalDate later) {
  long days=ChronoUnit.DAYS.between(earlier,later);
  return days>=25 && days<=35;
 }
}
