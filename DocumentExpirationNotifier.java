/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.anf.nmvps.webapp.scheduler;

import com.anf.nmvps.NmvpsConstants;
import com.anf.nmvps.model.*;
import com.anf.nmvps.model.onhold.HoldActionAudit;
import com.anf.nmvps.model.onhold.HoldActionService;
import com.anf.nmvps.service.*;
import com.anf.nmvps.util.DateUtil;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.opensymphony.xwork2.ActionSupport;

/**
 *
 * @author Kaibrah
 */
@Component
public class DocumentExpirationNotifier extends ActionSupport {
    private final Log log = LogFactory.getLog(DocumentExpirationNotifier.class);
    private final ProfileDocumentXrefManager profileDocumentXrefManager;
    private final ProfileAnfContactXrefManager profileAnfContactXrefManager;
    private final ContactManager contactManager;
    private final NMVPSConfigurationManager nmvpsConfigurationManager;
    private final EmailAddressManager emailAddressManager;
    
   @Autowired
    public DocumentExpirationNotifier(
            ProfileDocumentXrefManager profileDocumentXrefManager,
            ProfileAnfContactXrefManager profileAnfContactXrefManager,
            ContactManager contactManager,
            NMVPSConfigurationManager nmvpsConfigurationManager,
            EmailAddressManager emailAddressManager) {
        this.profileDocumentXrefManager = profileDocumentXrefManager;
        this.profileAnfContactXrefManager = profileAnfContactXrefManager;
        this.contactManager = contactManager;
        this.nmvpsConfigurationManager = nmvpsConfigurationManager;
        this.emailAddressManager = emailAddressManager;
    }
    @Autowired
	private ProfileManager profileManager;
    @Value("${mail.applicationURL}")
    private String appURL;
    @Value("${doc.expiration.subject}")
    private String messageSubject;
    @Value("${doc.expiration.generic.section1}")
    private String messageGenericSection1;
    @Value("${doc.expiration.france.section1}")
    private String messageFranceSection1;
    @Value("${doc.expiration.section2}")
    private String messageSection2;
    @Value("${doc.expiration.section3}")
    private String messageSection3;
    @Value("${doc.lastday.expiration.generic.section1}")
    private String messageLastDayGenericSection1;
    @Value("${doc.lastday.expiration.france.section1}")
    private String messageLastDayFranceSection1;
    @Value("${doc.holdprofile.subject}")
    private String holdProfileMessageSubject;
    @Value("${doc.holdprofile.section1}")
    private String holdProfileMessageSection1;
    @Value("${doc.holdprofile.section2}")
    private String holdProfileMessageSection2;
    /**
     * MailEngine for sending e-mail
     */
    protected MailEngine mailEngine;
    /**
     * A message pre-populated with default data
     */
    protected SimpleMailMessage mailMessage;
    /**
     * Velocity template to use for e-mailing
     */
    protected String templateName = "documentExpirationNotification.vm";

    public void setMailEngine(MailEngine mailEngine) {
        this.mailEngine = mailEngine;
    }

    public void setMailMessage(SimpleMailMessage mailMessage) {
        this.mailMessage = mailMessage;
    }
    
    @Scheduled(cron = "${nmvps.documentExpirationAlert.schedule}")
    public void runNotifyExpiringDocuments() {
        if(this.isRunScheduler("document expiration email warnings")) {
            this.notifyExpiringDocuments();
        }
    }
    
    @Scheduled(cron = "${nmvps.documentLastDayExpirationAlert.schedule}")
    public void runNotifyLastDayExpiringDocuments() {
        if(this.isRunScheduler("last day document expiration email alerts")) {
            this.notifyLastDayExpiringDocuments();
        }
    }
    
    @Scheduled(cron = "${nmvps.documentHoldAlert.schedule}")
    public void runHoldAlerts() {
        if(this.isRunScheduler("hold profile email alerts")) {
            this.sendHoldAlerts();
        }
    }
    
    /**
     * 
     * @param email email id of the recipient
     * @param greeting email greetings 
     * @param documentName name of the document that is set to expire
     * @param msg1 first paragraph. null if none
     * @param msg2 2nd paragraph
     * @param msg3 3rd paragraph
     */
    protected void sendNMVPSDocumentExpirationMessage(
            String[] email,
            String greeting,
            String documentName,
            String msg1,
            String msg2,
            String msg3) {
        this.templateName = "documentExpirationNotification.vm";
        this.mailMessage.setTo(email);
        this.mailMessage.setSubject(this.messageSubject);

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("greeting", greeting);
        if (msg1 != null && msg1.length() > 0) {
            model.put("message1", msg1);
        }
        if (msg2 != null && msg2.length() > 0) {
            model.put("message2", msg2);
        }
        if (msg3 != null && msg3.length() > 0) {
            model.put("message3", msg3);
        }
        model.put("documentName", documentName);
        model.put("applicationURL", this.appURL);
        this.mailEngine.sendMessage(this.mailMessage, this.templateName, model);
    }
    
    protected void sendProfileHoldListMessage(
            String[] email,
            String subject,
            String greeting,
            String msg1,
            String msg2,
            String msg3,
            HashMap<String, String> profileHoldList,
            String url) {
       // this.templateName = "emailListTemplate.vm";
        this.templateName ="vendorSetupRequest.vm";
        this.mailMessage.setTo(email);
        //this.mailMessage.setSubject(getText("vendor.hold.request.subject"));
        this.mailMessage.setSubject(subject);
        // TO DO work on greeting, cause we send this to different
        // departments
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("greeting", greeting);
        if (msg1 != null && msg1.length() > 0) {
            model.put("message1", msg1);
        }
        if (msg2 != null && msg2.length() > 0) {
            model.put("message2", msg2);
        }
        model.put("applicationURL", url);
        mailEngine.sendMessage(mailMessage, templateName, model);

    }

    public boolean isRunScheduler(String alertDescription) {
        try {
            if(StringUtils.equalsIgnoreCase(InetAddress.getLocalHost().getHostName(), 
                    this.nmvpsConfigurationManager.getEmailNotificationAppServer())) {
                log.debug("running " + alertDescription + " on: " + 
                        InetAddress.getLocalHost().getHostName());
                return true;
            }
            else {
                log.debug("This server is not set to run email alerts: " + 
                        InetAddress.getLocalHost().getHostName());
                return false;
            }
        } 
        catch (UnknownHostException ex) {
            log.error("Hostname cannot be resolved", ex);
        }
        catch (NullPointerException ex) {
            log.error("Hostname cannot be resolved", ex);
        }
        return false;
    }
    
    public void notifyExpiringDocuments() {
        List<ProfileDocumentXref> profileDocList = this.profileDocumentXrefManager.
                getDocumentExpirationList();
        log.debug("Found " + profileDocList.size() + " expiring documents");
        List<String> emailList;
        for(ProfileDocumentXref profileDocument: profileDocList) {
            emailList = new ArrayList<String>();
            //get profile primary contact
            Contact profileContact = this.contactManager.
                    getPrimaryContact(profileDocument.getPkey().getProfile().getId());
            if(profileContact != null) {
                emailList.add(profileContact.getEmail());
            }
            //get internal ANF primary contact
            ProfileAnfContactXref profileAnfContactXref = this.profileAnfContactXrefManager.
                    getProfilePrimaryAnfContact(profileDocument.getPkey().getProfile().getId());
            if(profileAnfContactXref != null) {
                emailList.add(profileAnfContactXref.getPkey().getAnfContact().getEmail());
            }
            if(!emailList.isEmpty()) {
                String[] emailArray = new String[emailList.size()];
                if(this.sendDocumentExpirationAlert(emailList.toArray(
                        emailArray), profileDocument, profileAnfContactXref)) {
                    //update notification date
                    this.profileDocumentXrefManager.updateProfileDocumentNotificationDate(
                            profileDocument.getPkey().getProfile().getId(), 
                            profileDocument.getPkey().getUploadDocumentName(), 
                            new Date());
                }
            }
        }
    }
    
    public void notifyLastDayExpiringDocuments() {
        List<ProfileDocumentXref> profileDocList = this.profileDocumentXrefManager.
                getLastDayDocumentExpirationList();
        log.debug("Found " + profileDocList.size() + " last day expiring documents");
        List<String> emailList;
        for(ProfileDocumentXref profileDocument: profileDocList) {
            emailList = new ArrayList<String>();
            //get profile primary contact
            Contact profileContact = this.contactManager.
                    getPrimaryContact(profileDocument.getPkey().getProfile().getId());
            if(profileContact != null) {
                emailList.add(profileContact.getEmail());
            }
            //get internal ANF primary contact
            ProfileAnfContactXref profileAnfContactXref = this.profileAnfContactXrefManager.
                    getProfilePrimaryAnfContact(profileDocument.getPkey().getProfile().getId());
            if(profileAnfContactXref != null) {
                emailList.add(profileAnfContactXref.getPkey().getAnfContact().getEmail());
            }
            if(!emailList.isEmpty()) {
                String[] emailArray = new String[emailList.size()];
                if(this.sendLastDayDocumentExpirationAlert(emailList.toArray(
                        emailArray), profileDocument, profileAnfContactXref)) {
                    //update notification date
                    this.profileDocumentXrefManager.updateProfileDocumentNotificationDate(
                            profileDocument.getPkey().getProfile().getId(), 
                            profileDocument.getPkey().getUploadDocumentName(), 
                            new Date());
                }
            }
        }
    }
    
    private boolean notifyProfileHoldList(
            String[] emailList, 
            HashMap<String, String> profileHoldList) {


        Iterator it = profileHoldList.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            List<Object> greetingArgs = new ArrayList<Object>();
            List<Object> msgArgs1 = new ArrayList<Object>();
            List<Object> msgArgs2 = new ArrayList<Object>();
            //msgArgs1.add(pair.getValue());
            greetingArgs.add("");
            msgArgs1.add("System Generated");

            msgArgs2.add("Document Expired");
            //msgArgs2.add("");
            // Send an account information e-mail
            String[] VendorDoc= pair.getValue().toString().split("=");
            String message1 = MessageFormat.format(this.holdProfileMessageSection1,
                    msgArgs1,VendorDoc[0]);
            String message2 = MessageFormat.format(this.holdProfileMessageSection2,
                    msgArgs2,VendorDoc[1]);


            try {
                this.sendProfileHoldListMessage(
                        emailList,
                        this.holdProfileMessageSubject,
                        "Hello",
                        message1,
                        message2,
                        null,
                        profileHoldList,
                        this.appURL);

            } catch (MailException me) {
                log.debug("mail exception: " + me);
                return false;
            }
        }
        return true;
    }
    
    public void sendHoldAlerts() {
        List<ProfileDocumentXref> profileDocList = this.profileDocumentXrefManager.
                getHoldNotificationList();
        //Add hold
        HashSet<Long> ProfileIds= new  HashSet<Long>();
        HoldActionService HoldActionServiceObj = new HoldActionService();
        HoldActionAudit HoldActionAuditObj =new HoldActionAudit();
        List<NMVPSConfiguration> HoldReasonList = this.nmvpsConfigurationManager.getHoldReasonList();
        List<NMVPSConfiguration> HoldActionList = this.nmvpsConfigurationManager.getOffHoldActionList();
        NMVPSConfiguration newReasonHoldAction = new NMVPSConfiguration();
        NMVPSConfiguration holdRequestHoldAction = new NMVPSConfiguration();
        NMVPSConfiguration holdReason = new NMVPSConfiguration();

        for(NMVPSConfiguration Temp:HoldActionList){
        	if("On hold request added".equalsIgnoreCase(Temp.getValue()))
        		holdRequestHoldAction=Temp;
        		//HoldActionAuditObj.setHoldActionNmvpsConfig(Temp);
        	else if("Additional on Hold request".equalsIgnoreCase(Temp.getValue()))
        		newReasonHoldAction=Temp;

        }
        for(NMVPSConfiguration Temp:HoldReasonList){
            if("Document Expired".equalsIgnoreCase(Temp.getValue()))
            {
                holdReason=Temp;
                break;
            }
                //HoldActionAuditObj.setHoldActionNmvpsConfig(Temp);
        }
    	HoldActionAuditObj.setCreateUserID("IDL");
    	HoldActionAuditObj.setOtherReasonFreeText("System Generated");
        HoldActionAuditObj.setHoldReasonNmvpsConfig(holdReason);
        //
        log.debug("Found " + profileDocList.size() + " profiles for hold alerts");
        if(!profileDocList.isEmpty()) {
            List<String> emailAddressList;
            EmailAddress emailAddress = this.emailAddressManager.getEmailAddressByGroup("GEMS");

            if(emailAddress != null) {
                emailAddressList = new ArrayList<String>();
                emailAddressList.add(emailAddress.getEmailAddress());
                emailAddressList.add("karim_Ibrahim@abercrombie.com");
                HashMap<String, String> holdProfileList = new HashMap<String, String>();
                int i = 1;
                for(ProfileDocumentXref profileDocument: profileDocList) {
                    holdProfileList.put(Integer.toString(i),profileDocument.getPkey().getProfile().getProfileUserId().substring(1) +
                                    " - " +
                                    profileDocument.getPkey().getProfile().getProfileName() +
                            "="
                            +
                            profileDocument.getPkey().getDocument().getDescription() + 
                            " (" + DateUtil.getDateTime("MM-dd-yyyy", profileDocument.getDocumentExpirationDate()) + ")");
                    i++;
                    ProfileIds.add( profileDocument.getPkey().getProfile().getId());
                }
                String[] emailArray = new String[emailAddressList.size()];
                // Insert hold request with document expired as reason
                if(!ProfileIds.isEmpty())
                {
                	for(Long ProfileId :ProfileIds)
                	{
                		HoldActionAuditObj.setVendorProfileGID(ProfileId);
                		if("on_hold".equalsIgnoreCase(HoldActionServiceObj.getHoldByVendorId(ProfileId).getHoldActionNmvpsConfig().getProperty()))
                			HoldActionAuditObj.setHoldActionNmvpsConfig(newReasonHoldAction);
                		else
                			HoldActionAuditObj.setHoldActionNmvpsConfig(holdRequestHoldAction);
                		HoldActionServiceObj.insertHoldAudit(HoldActionAuditObj);

        				// Update profile record to add the hold action in the profile
        				// table
        				this.profileManager.updateProfileHoldStatus(ProfileId,HoldActionAuditObj.getHoldActionNmvpsConfig());
                	}

                }
                this.notifyProfileHoldList(emailAddressList.toArray(emailArray), holdProfileList);
            }
        }
    }
    
    public boolean sendDocumentExpirationAlert(
            String[] emailList, 
            ProfileDocumentXref profileDocument, 
            ProfileAnfContactXref profileAnfContactXref) {
        String message1;
        if(profileDocument.getPkey().getDocument().getName().
                matches(NmvpsConstants.DOCUMENT_CERT_OF_VIGILANCE + "|" + 
                        NmvpsConstants.DOCUMENT_KBIS)) {
            message1 = MessageFormat.format(this.messageFranceSection1, 
                    profileDocument.getPkey().getProfile().getProfileName(),
                    profileDocument.getPkey().getProfile().getProfileUserId(),
                    DateUtil.getDateTime("MM-dd-yyyy", profileDocument.getDocumentExpirationDate()) + "(MM-DD-YYYY)");
        }
        else {
            message1 = MessageFormat.format(this.messageGenericSection1, 
                    profileDocument.getPkey().getProfile().getProfileName(),
                    profileDocument.getPkey().getProfile().getProfileUserId(),
                    DateUtil.getDateTime("MM-dd-yyyy", profileDocument.getDocumentExpirationDate()) + "(MM-DD-YYYY)");
        }
        String message2 = this.messageSection2;
        String message3 = "";
        if(profileAnfContactXref != null) {
            message3 = MessageFormat.format(this.messageSection3, 
                    profileAnfContactXref.getPkey().getAnfContact().getUserName(), 
                    profileAnfContactXref.getPkey().getAnfContact().getEmail());
        }
        try {
            this.sendNMVPSDocumentExpirationMessage(
                    emailList,
                    profileDocument.getPkey().getProfile().getProfileName(),
                    "1) " + profileDocument.getPkey().getDocument().getDescription(),
                    message1,
                    message2,
                    message3);
        } catch (MailException me) {
            log.debug("mail exception: " + me);
            return false;
        }
        return true;
    }

    public boolean sendLastDayDocumentExpirationAlert(
            String[] emailList, 
            ProfileDocumentXref profileDocument, 
            ProfileAnfContactXref profileAnfContactXref) {
        String message1;
        if(profileDocument.getPkey().getDocument().getName().
                matches(NmvpsConstants.DOCUMENT_CERT_OF_VIGILANCE + "|" + 
                        NmvpsConstants.DOCUMENT_KBIS)) {
            message1 = MessageFormat.format(this.messageLastDayFranceSection1, 
                    profileDocument.getPkey().getProfile().getProfileName(),
                    profileDocument.getPkey().getProfile().getProfileUserId(),
                    DateUtil.getDateTime("MM-dd-yyyy", profileDocument.getDocumentExpirationDate()) + "(MM-DD-YYYY)");
        }
        else {
            message1 = MessageFormat.format(this.messageLastDayGenericSection1, 
                    profileDocument.getPkey().getProfile().getProfileName(),
                    profileDocument.getPkey().getProfile().getProfileUserId(),
                    DateUtil.getDateTime("MM-dd-yyyy", profileDocument.getDocumentExpirationDate()) + "(MM-DD-YYYY)");
        }
        String message2 = this.messageSection2;
        String message3 = "";
        if(profileAnfContactXref != null) {
            message3 = MessageFormat.format(this.messageSection3, 
                    profileAnfContactXref.getPkey().getAnfContact().getUserName(), 
                    profileAnfContactXref.getPkey().getAnfContact().getEmail());
        }
        try {
            this.sendNMVPSDocumentExpirationMessage(
                    emailList,
                    profileDocument.getPkey().getProfile().getProfileName(),
                    "1) " + profileDocument.getPkey().getDocument().getDescription(),
                    message1,
                    message2,
                    message3);
        } catch (MailException me) {
            log.debug("mail exception: " + me);
            return false;
        }
        return true;
    }
    
}
