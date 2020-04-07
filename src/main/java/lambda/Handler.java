package lambda;


import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;


import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

// Handler value: Handler
public class Handler implements RequestHandler<SNSEvent, Object>{
    AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
    public Object handleRequest(SNSEvent snsEvent, Context context)
    {

        LambdaLogger logger = context.getLogger();
        String response = new String("200 OK");
        // log execution details
        String message=snsEvent.getRecords().get(0).getSNS().getMessage();
        logger.log("message receive:"+message);
        List<String> a = Arrays.asList(message.split(","));
        if(a.size()<=2) return response;
        String email = a.get(0);
        logger.log("email:"+email);
        String days = a.get(1);
        List<String> billid = a.subList(2,a.size());
        List<String> links=new ArrayList<>();
        for(String lk:billid) {
            lk="http://prod.qingc.me/v1/bill/"+lk;
            links.add(lk);
        }
        if(!checkToken(email)){
            generateToken(email);
            logger.log("generateToken");
            //send email to do
            sendEmail(links,days,email);
        }
        else{
            logger.log("Request sent within 1 hour");
        }

        return response;
    }

    public void sendEmail(List<String> links, String days,String email){

        // Replace sender@example.com with your "From" address.
        // This address must be verified with Amazon SES.
        String FROM = "bill@prod.qingc.me";

        // Replace recipient@example.com with a "To" address. If your account
        // is still in the sandbox, this address must be verified.
        String TO = email;

        // The configuration set to use for this email. If you do not want to use a
        // configuration set, comment the following variable and the
        // .withConfigurationSetName(CONFIGSET); argument below.
        //String CONFIGSET = "ConfigSet";

        // The subject line for the email.
        String SUBJECT = "Due bill notification";

        // The email body for recipients with non-HTML email clients.
        String TEXTBODY = "Dear customer:"+"\n"+"\n"+"Here are bill links that due within "+days+" days"+"\n";
        for(String link:links){
            TEXTBODY=TEXTBODY+"\n"+"  "+link;
        }
        try {
            AmazonSimpleEmailService client =
                    AmazonSimpleEmailServiceClientBuilder.standard()
                            // Replace US_WEST_2 with the AWS Region you're using for
                            // Amazon SES.
                            .withRegion(Regions.US_EAST_1).build();
            SendEmailRequest request = new SendEmailRequest()
                    .withDestination(
                            new Destination().withToAddresses(TO))
                    .withMessage(new Message()
                            .withBody(new Body()
                                    .withText(new Content()
                                            .withCharset("UTF-8").withData(TEXTBODY)))
                            .withSubject(new Content()
                                    .withCharset("UTF-8").withData(SUBJECT)))
                    .withSource(FROM);
            // Comment or remove the next line if you are not using a
            // configuration set
            //.withConfigurationSetName(CONFIGSET);
            client.sendEmail(request);
            System.out.println("Email sent!");
        } catch (Exception ex) {
            System.out.println("The email was not sent. Error message: "
                    + ex.getMessage());
        }
    }


    public void generateToken(String email){
        HashMap<String,AttributeValue> item_values =
                new HashMap<>();
        item_values.put("Email", new AttributeValue(email));
        item_values.put("Token", new AttributeValue(generateNewToken()));
        item_values.put("ttl", new AttributeValue(""+(System.currentTimeMillis() / 1000L + 3600)));
        try {
            client.putItem("DynamoDBForLambda", item_values);
        }catch (ResourceNotFoundException e) {
            System.err.format("Error: The table \"%s\" can't be found.\n", "DynamoDBForLambda");
            System.err.println("Be sure that it exists and that you've typed its name correctly!");
            System.exit(1);
        } catch (AmazonServiceException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

    }


    private static final SecureRandom secureRandom = new SecureRandom(); //threadsafe
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder(); //threadsafe
    public static String generateNewToken() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }
    //check if token exist.
    public boolean checkToken(String email) {

        ScanRequest scanRequest = new ScanRequest()
                .withTableName("DynamoDBForLambda");
        try {
            ScanResult result = client.scan(scanRequest);
            for (Map<String, AttributeValue> item : result.getItems()) {
                if(item.get("Email").getS().equals(email)){
                    Long ttl=Long.valueOf(item.get("ttl").getS());//guo qi sj
                    //System.out.println(ttl);
                    Long currenttime=System.currentTimeMillis() / 1000L;
                    if(ttl>currenttime)
                        return true;//mei guo qi
                    else
                        return false;// guo qi
                }
            }


        } catch (AmazonDynamoDBException e) {
            e.getStackTrace();

        }
        return false;// bu cun zai
            /*
            if (result !=null && result.getItem() != null) {

                AttributeValue ttl = result.getItem().get("ttl");
                Long ttl1= Long.valueOf(ttl.toString());
                Long currenttime=System.currentTimeMillis() / 1000L;
                if(ttl1>currenttime)
                    return true;
                else
                    return false;
            } else {
                return false;
            }

             */

    }
}
