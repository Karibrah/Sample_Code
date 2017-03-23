/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.anf.nmvps.webapp.ajax.action;

import com.anf.nmvps.model.AnfContact;
import com.anf.nmvps.model.Contact;
import com.anf.nmvps.model.NMVPSConfiguration;
import com.anf.nmvps.model.Profile;
import com.anf.nmvps.model.ProfileAnfContactXref;
import com.anf.nmvps.service.VendorManager;
import com.anf.nmvps.service.ProfileManager;
import com.anf.nmvps.service.UserExistsException;
import com.anf.nmvps.service.EmailAddressManager;
import com.anf.nmvps.service.ProfileAnfContactXrefManager;
import com.anf.nmvps.model.onhold.HoldActionAudit;
import com.anf.nmvps.model.onhold.HoldActionService;
import com.anf.nmvps.service.NMVPSConfigurationManager;
import com.google.gson.Gson;
import static com.opensymphony.xwork2.Action.SUCCESS;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.hibernate.ObjectDeletedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import com.anf.nmvps.webapp.manager.NotificationManager;
import com.anf.nmvps.ldap.LdapUserManager;

/**
 *
 * @author Kaibrah
 */
public class HoldAjaxAction extends BaseAjaxAction {

	private InputStream inputStream;
	@Autowired
	private NMVPSConfigurationManager nmvpsConfigurationManager;
	@Autowired
	private NotificationManager notificationManager;
	@Autowired
	private VendorManager vendorManager;
	@Autowired
	private ProfileManager profileManager;
	@Autowired
	private EmailAddressManager emailAddressManager;
	@Autowired
	private ProfileAnfContactXrefManager profileAnfContactXrefManager;
	@Autowired
	private LdapUserManager ldapUserManager;

	/**
	 * add hold action to vendor has the values passed as parameters:
	 * 
	 * 
	 */
	public String addVendorHoldAction() {
		Gson gson = new Gson();
		log.debug("Add On hold 1");
		if (this.getRequest().getParameter("ProfileId") != null && this.getRequest().getParameter("ActionId") != null
				&& this.getRequest().getParameter("ReasonId") != null) {
			//log.debug("Add On hold: 2");
			// set the hold audit object attributes
			Long ProfileGid = Long.parseLong(this.getRequest().getParameter("ProfileId"));
			Long ActionId = Long.parseLong(this.getRequest().getParameter("ActionId"));
			Long ReasonId = Long.parseLong(this.getRequest().getParameter("ReasonId"));
			NMVPSConfiguration holdActionNmvpsConfigReason = new NMVPSConfiguration();
			NMVPSConfiguration holdActionNmvpsConfigAction = new NMVPSConfiguration();
			holdActionNmvpsConfigAction.setId(ActionId);
			holdActionNmvpsConfigReason.setId(ReasonId);
			HoldActionAudit holdActionObj = new HoldActionAudit();
			holdActionObj.setVendorProfileGID(ProfileGid);
			holdActionObj.setCreateUserID(this.getRequest().getRemoteUser());
			holdActionObj.setHoldReasonNmvpsConfig(holdActionNmvpsConfigReason);
			holdActionObj.setHoldActionNmvpsConfig(holdActionNmvpsConfigAction);
			holdActionObj.setOtherReasonFreeText(this.getRequest().getParameter("OtherTxt"));

			// initialize the Hold service to call the insert method in the try
			HoldActionService holdActionServiceObj = new HoldActionService();
			
			try {

				holdActionServiceObj.insertHoldAudit(holdActionObj);
				// Update profile record to add the hold action in the profile
				// table
				
				this.profileManager.updateProfileHoldStatus(ProfileGid,holdActionNmvpsConfigAction);
				

				// Handle the email notification
				// if request Gems and internal contact are notified
				// if vendor put on hold or taken off hold then GEMS ,internal
				// contact and Vendor's primary contact are notified
				// Get GEMS email address
				
				String GEMSEmail = this.emailAddressManager.getEmailAddressByGroup("GEMS").getEmailAddress();
				
				String ActionTaken = this.nmvpsConfigurationManager.get(ActionId).getValue();
				// Get anf internal contact
				
				String anfInternalContact ="";
				ProfileAnfContactXref profileAnfContactXref= this.profileAnfContactXrefManager.getProfilePrimaryAnfContact(ProfileGid);
				if(profileAnfContactXref!=null){
					AnfContact anfContact= profileAnfContactXref.getPkey().getAnfContact();
					if(anfContact !=null)
						anfInternalContact = anfContact.getEmail();
				}
				
				// if Vendor is on hold
				if ("Put on hold".equals(ActionTaken) || "Accept on hold request".equals(ActionTaken)) {
					
					// Get Vendor Primary contact
					List<Contact> profile_contacts = this.profileManager.get(ProfileGid).getContacts();
					String Primary_Contact_Email = "";
					String Primary_Contact_Name = "";
					for (Contact Temp : profile_contacts) {
						if ("primary".equals(Temp.getContactTypeNmvpsConfig().getValue()))
							{
								Primary_Contact_Email = Temp.getEmail();
								Primary_Contact_Name = Temp.getAttention();
								break;
							}
					}
					String[] emailList= { };
					if (!anfInternalContact.equals("")  && !Primary_Contact_Email.equals(""))
						emailList = new String[] { anfInternalContact, Primary_Contact_Email };
					else if (anfInternalContact.equals("")  && !Primary_Contact_Email.equals(""))
						emailList = new String[] { Primary_Contact_Email };
					else if (!anfInternalContact.equals("")  && Primary_Contact_Email.equals(""))
						emailList = new String[] {  anfInternalContact };
					//log.debug("Add On hold: 5");	
					if("Other".equals(this.nmvpsConfigurationManager.get(ReasonId).getValue() ))
						this.notificationManager.sendVendorHoldNotification(
							this.vendorManager.getVendorByprofileId(this.getRequest().getParameter("ProfileId")),
							emailList, this.nmvpsConfigurationManager.get(ReasonId).getValue() +" - "+ this.getRequest().getParameter("OtherTxt") , anfInternalContact,Primary_Contact_Name,Primary_Contact_Email);
					else
						this.notificationManager.sendVendorHoldNotification(
								this.vendorManager.getVendorByprofileId(this.getRequest().getParameter("ProfileId")),
								emailList, this.nmvpsConfigurationManager.get(ReasonId).getValue() , anfInternalContact,Primary_Contact_Name,Primary_Contact_Email);
						
						
				}
				else if("Reject off hold request".equals(ActionTaken)|| "Reject on hold request ".equals(ActionTaken) ){
					//
					if(!"IDL".equals(this.getRequest().getParameter("CreateUser"))) {
						String requester_email=this.getRequest().getParameter("CreateUser")+"@anfcorp.com";
						String[] emailList = {requester_email};
						this.notificationManager.sendRejectionHoldRequest(
								this.vendorManager.getVendorByprofileId(this.getRequest().getParameter("ProfileId")),
								emailList, this.nmvpsConfigurationManager.get(ReasonId).getValue() + " - " + this.getRequest().getParameter("OtherTxt"),
								"Reject off hold request".equals(ActionTaken));
					}
				}
				// if Vendor is off hold
				else if ("Taken off hold".equals(ActionTaken) || "Accept off hold request".equals(ActionTaken)) {
					
					// Get Vendor Primary contact
					List<Contact> profile_contacts = this.profileManager.get(ProfileGid).getContacts();
					String Primary_Contact_Email = "";
					String Primary_Contact_Name = "";
					for (Contact Temp : profile_contacts) {
						if ("primary".equals(Temp.getContactTypeNmvpsConfig().getValue()))
							{
								Primary_Contact_Email = Temp.getEmail();
								Primary_Contact_Name = Temp.getAttention();
								break;
							}
					}
					String[] emailList= { };
					if (!anfInternalContact.equals("")  && !Primary_Contact_Email.equals(""))
						emailList = new String[] {  anfInternalContact, Primary_Contact_Email };
					else if (anfInternalContact.equals("")  && !Primary_Contact_Email.equals(""))
						emailList = new String[] {  Primary_Contact_Email };
					else if (!anfInternalContact.equals("")  && Primary_Contact_Email.equals(""))
						emailList = new String[] {  anfInternalContact };
				
					this.notificationManager.sendVendorOffHoldNotification(
							this.vendorManager.getVendorByprofileId(this.getRequest().getParameter("ProfileId")),
							emailList, anfInternalContact,Primary_Contact_Name);
				}
				// if on/off hold request is added
				else {
					String[] emailList = { GEMSEmail};
				    if (!anfInternalContact.equals("") )
						emailList = new String[] { GEMSEmail };
					this.notificationManager.sendVendorHoldRequestNotification(
							this.vendorManager.getVendorByprofileId(this.getRequest().getParameter("ProfileId")),
							emailList, this.nmvpsConfigurationManager.get(ReasonId).getValue(),
							this.getRequest().getParameter("OtherTxt"),
							this.nmvpsConfigurationManager.get(ActionId).getValue(),
							this.ldapUserManager.getFullName(this.getRequest().getRemoteUser()));
				}

				String[] strings = { "success", "Hold action was successfully added." };
				this.setInputStream(new ByteArrayInputStream(gson.toJson(strings).getBytes()));
				return SUCCESS;
			} catch (InvalidDataAccessApiUsageException e) {
				log.debug("Error Hold action request: " + ProfileGid, e);
			} catch (ObjectDeletedException e) {
				log.debug("Error Hold action request: " + ProfileGid, e);
			} 
		}
		String[] strings = { "failure",
				"There was a problem Hold action request. " + "Please contact our service desk." };
		this.setInputStream(new ByteArrayInputStream(gson.toJson(strings).getBytes()));
		return SUCCESS;
	}

	// Add new reason to reason list
	public String addHoldReason() {
		Gson gson = new Gson();
		//log.debug("Add On hold 1");
		if (this.getRequest().getParameter("ReasonTxt") != null) {
			//log.debug("Add On hold: 2");
			// set the NMVPS Configuration object attributes
			String ReasonTxt = (this.getRequest().getParameter("ReasonTxt"));
			String Group = "hold_reason";
			Long Key = new Long(this.nmvpsConfigurationManager.getHoldReasonList().size() + 1);
			Date createDate = new Date();
			String User = (this.getRequest().getParameter("UserId"));
			NMVPSConfiguration holdActionNmvpsConfigReason = new NMVPSConfiguration();
			holdActionNmvpsConfigReason.setCreatedOn(createDate);
			holdActionNmvpsConfigReason.setCreatedBy(User);
			holdActionNmvpsConfigReason.setKey(Key.toString());
			holdActionNmvpsConfigReason.setProperty(Group);
			holdActionNmvpsConfigReason.setValue(ReasonTxt);
			try {
				this.nmvpsConfigurationManager.saveNMVPSConfiguration(holdActionNmvpsConfigReason);
				String[] strings = { "success", "Hold reason was successfully added." };
				this.setInputStream(new ByteArrayInputStream(gson.toJson(strings).getBytes()));
				return SUCCESS;
			} catch (InvalidDataAccessApiUsageException e) {
				log.debug("Error Hold reason request: " + ReasonTxt, e);
			} catch (ObjectDeletedException e) {
				log.debug("Error Hold reason request: " + ReasonTxt, e);
			}
		}
		String[] strings = { "failure",
				"There was a problem Hold reason request. " + "Please contact our service desk." };
		this.setInputStream(new ByteArrayInputStream(gson.toJson(strings).getBytes()));
		return SUCCESS;
	}
	
	
	// Add new reason to reason list
	public String addNewHoldReason(){
		Gson gson = new Gson();
		//log.debug("Add On hold 1 ");

		String addList = (this.getRequest().getParameter("AddList"));
		String removeList = (this.getRequest().getParameter("RemoveList"));
		String preSelectedList = (this.getRequest().getParameter("PreSelectedList"));
		Long removeActionId = Long.parseLong(this.getRequest().getParameter("RemoveActionId"));
		Long addActionId = Long.parseLong(this.getRequest().getParameter("AddActionId"));
		Long ProfileId=Long.parseLong(this.getRequest().getParameter("ProfileId"));
		
		NMVPSConfiguration holdActionNmvpsConfigReason = new NMVPSConfiguration();
		NMVPSConfiguration holdAddActionNmvpsConfigAction = new NMVPSConfiguration();
		NMVPSConfiguration holdRemoveActionNmvpsConfigAction = new NMVPSConfiguration();
		
		holdAddActionNmvpsConfigAction.setId(addActionId);
		holdRemoveActionNmvpsConfigAction.setId(removeActionId);
		HoldActionAudit holdActionObj = new HoldActionAudit();
		holdActionObj.setVendorProfileGID(ProfileId);
		holdActionObj.setOtherReasonFreeText(this.getRequest().getParameter("ReasonTxt"));
		holdActionObj.setCreateUserID(this.getRequest().getRemoteUser());
	
		
		List<String> addListArr = Arrays.asList(addList.split(","));
		List<Long> addListLong = new ArrayList<Long>();
		for(String item :addListArr ){
			if(!"".equals(item.trim()))
				addListLong.add(Long.parseLong(item.trim()));
		}
		
		List<String> removeListArr = Arrays.asList(removeList.split(","));
		List<Long> removeListLong = new ArrayList<Long>();
		for(String item :removeListArr ){
			if(!"".equals(item.trim()))
				removeListLong.add(Long.parseLong(item.trim()));
		}
		
		List<String> preSelectedListArr = Arrays.asList(preSelectedList.split(","));
		List<Long> preSelectedListLong = new ArrayList<Long>();
		
		for(String item :preSelectedListArr ){
			
			if(!"".equals(item.trim()))
				preSelectedListLong.add(Long.parseLong(item.trim()));
		}
		boolean chnageHappened=false;
		
		boolean profileUpdate=true;
		HoldActionService holdActionServiceObj = new HoldActionService();
		for(Long item :removeListLong){
			if(preSelectedListLong.contains(item)){
				//log.debug("Add On hold 1 Remove: "+item);
				holdActionNmvpsConfigReason.setId(item);
				holdActionObj.setHoldReasonNmvpsConfig(holdActionNmvpsConfigReason);
				holdActionObj.setHoldActionNmvpsConfig(holdRemoveActionNmvpsConfigAction);
				// initialize the Hold service to call the insert method in the try
				
				
				try {
					
					holdActionServiceObj.insertHoldAudit(holdActionObj);
					// Update profile record to add the hold action in the profile
					// table
					if(profileUpdate)
						{
							this.profileManager.updateProfileHoldStatus(ProfileId,holdRemoveActionNmvpsConfigAction);
							profileUpdate=false;
							
						}
					chnageHappened=true;
									
				} catch (InvalidDataAccessApiUsageException e) {
					log.debug("Error Hold action request: " + ProfileId, e);
				} catch (ObjectDeletedException e) {
					log.debug("Error Hold action request: " + ProfileId, e);
				}
				
			}
		}
		profileUpdate=false;
		for(Long item :addListLong){
			if(!preSelectedListLong.contains(item)){
				//log.debug("Add On hold 1 Add:"+item);
				holdActionNmvpsConfigReason.setId(item);
				holdActionObj.setHoldReasonNmvpsConfig(holdActionNmvpsConfigReason);
				holdActionObj.setHoldActionNmvpsConfig(holdAddActionNmvpsConfigAction);
				// initialize the Hold service to call the insert method in the try
				
				
				try {

					holdActionServiceObj.insertHoldAudit(holdActionObj);
					// Update profile record to add the hold action in the profile
					// table
					if(!profileUpdate)
					{
						this.profileManager.updateProfileHoldStatus(ProfileId,holdAddActionNmvpsConfigAction);
						profileUpdate=true;
					}
					chnageHappened=true;
										
				} catch (InvalidDataAccessApiUsageException e) {
					log.debug("Error Hold action request: " + ProfileId, e);
				} catch (ObjectDeletedException e) {
					log.debug("Error Hold action request: " + ProfileId, e);
				}
			
			}
			
		}
		
		
		if(removeListLong.containsAll(preSelectedListLong) && !preSelectedListLong.isEmpty() && addListLong.isEmpty())
		{
			
			holdRemoveActionNmvpsConfigAction.setId(Long.parseLong(this.getRequest().getParameter("TakeOffHoldId")));
			
			holdActionObj.setHoldActionNmvpsConfig(holdRemoveActionNmvpsConfigAction);
			
//			// initialize the Hold service to call the insert method in the try
//			HoldActionService holdActionServiceObj = new HoldActionService();
//			
			try {
				log.debug("HoldActionObj:"+holdActionObj.getVendorProfileGID()+" "+holdActionObj.getOtherReasonFreeText()+" "+holdActionObj.getHoldActionNmvpsConfig().getId()+ " "+holdActionObj.getHoldReasonNmvpsConfig().getId());
				holdActionServiceObj.insertHoldAudit(holdActionObj);
				// Update profile record to add the hold action in the profile
				// table
				
				this.profileManager.updateProfileHoldStatus(ProfileId,holdRemoveActionNmvpsConfigAction);
				
				//send email notification 
				String anfInternalContact ="";
				String GEMSEmail = this.emailAddressManager.getEmailAddressByGroup("GEMS").getEmailAddress();
				ProfileAnfContactXref profileAnfContactXref= this.profileAnfContactXrefManager.getProfilePrimaryAnfContact(ProfileId);
				if(profileAnfContactXref!=null){
					AnfContact anfContact= profileAnfContactXref.getPkey().getAnfContact();
					if(anfContact !=null)
						anfInternalContact = anfContact.getEmail();
				}
				// Get Vendor Primary contact
				List<Contact> profile_contacts = this.profileManager.get(ProfileId).getContacts();
				String Primary_Contact_Email = "";
				String Primary_Contact_Name = "";
				for (Contact Temp : profile_contacts) {
					if ("primary".equals(Temp.getContactTypeNmvpsConfig().getValue()))
						{
							Primary_Contact_Email = Temp.getEmail();
							Primary_Contact_Name = Temp.getAttention();
							break;
						}
				}
				String[] emailList= { GEMSEmail};
				if (!anfInternalContact.equals("")  && !Primary_Contact_Email.equals(""))
					emailList = new String[] { GEMSEmail, anfInternalContact, Primary_Contact_Email };
				else if (anfInternalContact.equals("")  && !Primary_Contact_Email.equals(""))
					emailList = new String[] { GEMSEmail, Primary_Contact_Email };
				else if (!anfInternalContact.equals("")  && Primary_Contact_Email.equals(""))
					emailList = new String[] { GEMSEmail, anfInternalContact };
			
				this.notificationManager.sendVendorOffHoldNotification(
						this.vendorManager.getVendorByprofileId(this.getRequest().getParameter("ProfileId")),
						emailList, anfInternalContact,Primary_Contact_Name);
				
				String[] strings = { "success", "Hold action was successfully added." };
				this.setInputStream(new ByteArrayInputStream(gson.toJson(strings).getBytes()));
				return SUCCESS;
			
			} catch (InvalidDataAccessApiUsageException e) {
				log.debug("Error Hold action request: " + ProfileId, e);
			} catch (ObjectDeletedException e) {
				log.debug("Error Hold action request: " + ProfileId, e);
			}
			
	}
		//log.debug("Add On hold 1 "+addListLong.toString()+ "-"+removeListLong.toString()+"-"+preSelectedListLong.toString()+"-"+removeActionId+"-"+addActionId);
		
		String[] strings = { "success", "Hold action was successfully added." };
		if( ! chnageHappened )
			strings[0] = "NoChange";
		this.setInputStream(new ByteArrayInputStream(gson.toJson(strings).getBytes()));
		return SUCCESS;
		
	
		
	}

	public InputStream getInputStream() {
		return inputStream;
	}

	public void setInputStream(InputStream inputStream) {
		this.inputStream = inputStream;
	}

}
