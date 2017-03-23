getwd()
setwd("/Users/hvaidyanathan/Desktop/Harrisburg/Sem1/ANLY 500-51-2016 - Maher/Datasets")

#Read both CSV files for LA Crimes and Seattle Police Department Incidence Responses
lacrimes <- read.csv("LA-Crimes_2012-2015.csv")
seattledata <- read.csv("Seattle_Police_Department_911_Incident_Response.csv")
head(lacrimes)
head(seattledata)
str(seattledata)

NAdata_seattle <- seattledata[rowSums(is.na(lacrimes))>0,]

#Retrieve only the specific columns for analysis by subsetting data
la_subset <- lacrimes[,c(1,3,4,6,9,11)]
seattle_subset <- seattledata[, c(4,5,6,7,8,10,11)]

#Convert Date Reported and Date Occured to date based columns
la_subset$Date.Rptd <- as.Date(la_subset$Date.Rptd,format= "%m/%d/%Y")
la_subset$DATE.OCC <- as.Date(la_subset$DATE.OCC,format= "%m/%d/%Y")

#Compute difference between when crime occured and when crime was reported
diff_in_days <- as.numeric(difftime(la_subset$Date.Rptd, la_subset$DATE.OCC, units="days"))

#Add a new column days_in_diff, derived from the above computation
la_subset$days_in_diff <- diff_in_days
str(diff_in_days)
str(la_subset)

#There were a few rows where the reported date was sooner than the occured date. We removed those values through subsetting
la_subset_negative <- subset(la_subset, la_subset$days_in_diff < 0) 
la_subset_positive <- subset(la_subset, la_subset$days_in_diff >= 0)
unique(la_subset_positive$CrmCd.Desc) 
#There are 158 types of crimes in the dataset.

#Add two new columns, which specify the month and the year the crime was reported
la_subset_positive$monthRptd <- as.numeric(format(la_subset_positive$Date.Rptd,'%m'))
la_subset_positive$yearRptd <- as.numeric(format(la_subset_positive$Date.Rptd,'%y'))

#Identify trends in area distribution and crime resolution status in the city of LA
unique(la_subset_positive$AREA.NAME) # there are 21 different areas in the city of LA
unique(la_subset_positive$Status.Desc) # 6 unique types of result Status (Unknown, Invest Cont, Adult Other, Adult Arrest, Juv Arrest, Juv Other)

#Classification of crimes in the dataset as physical_threat, sexual_crimes and theft_crimes
physical_threat <- c('BRANDISH WEAPON', 'THREATS, VERBAL/TERRORIST','ASSAULT WITH DEADLY WEAPON, AGGRAVATED ASSAULT','SHOTS FIRED AT INHABITED DWELLING', 'CRIMINAL THREATS - NO WEAPON DISPLAYED', 'ASSAULT WITH DEADLY WEAPON ON POLICE OFFICER', 'CHILD ABUSE (PHYSICAL) - AGGRAVATED ASSAULT', 'SPOUSAL (COHAB) ABUSE - AGGRAVATED ASSAULT', 'CRIMINAL HOMICIDE', 'THREATENING PHONE CALLS/LETTERS', 'ARSON', 'BOMB SCARE', 'WEAPONS POSSESSION/BOMBING', 'SHOTS FIRED AT MOVING VEHICLE, TRAIN OR AIRCRAFT', 'LYNCHING', 'LYNCHING - ATTEMPTED', 'SHOTS INHABITED DWELLING', 'CHILD ENDANGERMENT/NEG.', 'HOMICIDE (NON-UCR)', 'KIDNAPPING', 'CHILD STEALING', 'MANSLAUGHTER, NEGLIGENT')
sexual_crimes <- c('RAPE, FORCIBLE', 'RAPE, ATTEMPTED', 'RAPE, ATTEMPTED', 'ORAL COPULATION', 'SEX, UNLAWFUL', 'SEXUAL PENTRATION WITH A FOREIGN OBJECT', 'SODOMY/SEXUAL CONTACT B/W PENIS OF ONE PERS TO ANUS OTH', 'SEX OFFENDER REGISTRANT INCIDENT', 'BEASTIALITY, CRIME AGAINST NATURE SEXUAL ASSLT WITH ANIM', 'INCEST (SEXUAL ACTS BETWEEN BLOOD RELATIVES)')
theft_crimes <- c('THEFT PLAIN - PETTY (UNDER $400)', 'BURGLARY', 'BUNCO, PETTY THEFT', 'VEHICLE - STOLEN', 'THEFT FROM MOTOR VEHICLE - PETTY ($950.01 & OVER)', 'THEFT-GRAND ($950.01 & OVER)EXCPT,GUNS,FOWL,LIVESTK,PROD', 'THEFT, PERSON', 'DEFRAUDING INNKEEPER/THEFT OF SERVICES, OVER $400', 'THEFT FROM MOTOR VEHICLE - GRAND ($400 AND OVER)', 'BURGLARY FROM VEHICLE', 'THEFT PLAIN - PETTY ($950 & UNDER)', 'BUNCO, GRAND THEFT', 'ROBBERY', 'SHOPLIFTING - PETTY THEFT ($950 & UNDER)', 'ATTEMPTED ROBBERY', 'BURGLARY, ATTEMPTED', 'SHOPLIFTING-GRAND THEFT ($950.01 & OVER)', 'THEFT OF IDENTITY', 'EMBEZZLEMENT, GRAND THEFT ($950.01 & OVER)', 'THEFT, COIN MACHINE - PETTY ($950 & UNDER)', 'CREDIT CARDS, FRAUD USE ($950.01 & OVER)', 'THEFT PLAIN - ATTEMPT', 'THEFT FROM MOTOR VEHICLE - ATTEMPT', 'PURSE SNATCHING', 'VEHICLE - ATTEMPT STOLEN', 'BIKE - STOLEN', 'EXTORTION', 'BURGLARY FROM VEHICLE, ATTEMPTED', 'SHOPLIFTING - ATTEMPT', 'DEFRAUDING INNKEEPER/THEFT OF SERVICES, $400 & UNDER', 'PICKPOCKET', 'EMBEZZLEMENT, PETTY THEFT ($950 & UNDER)', 'BOAT - STOLEN', 'CREDIT CARDS, FRAUD USE ($950 & UNDER', 'THEFT FROM PERSON - ATTEMPT', 'DISHONEST EMPLOYEE - GRAND THEFT', 'THEFT, COIN MACHINE - GRAND ($950.01 & OVER)', 'PURSE SNATCHING - ATTEMPT', 'THEFT, COIN MACHINE - ATTEMPT', 'DISHONEST EMPLOYEE - PETTY THEFT', 'GRAND THEFT / AUTO REPAIR', 'THEFT FROM MOTOR VEHICLE - PETTY (UNDER $400)', 'SHOPLIFTING - PETTY THEFT', 'THEFT-GRAND (OVER $400 OR $100 IF FOWL)', 'EMBEZZLEMENT, PETTY', 'EMBEZZLEMENT, GRAND', 'SHOPLIFTING-GRAND THEFT (OVER $401)', 'CREDIT CD > $200', 'CREDIT DD < $200', 'THEFT, COIN MACHINE - PETTY', 'PETTY THEFT - AUTO REPAIR', 'BIKE - ATTEMPTED STOLEN', 'PICKPOCKET, ATTEMPT', 'THEFT, COIN MACHINE - GRAND', 'TILL TAP - GRAND THEFT ($950.01 & OVER)', 'GRAND THEFT / INSURANCE FRAUD', 'DISHONEST EMPLOYEE ATTEMPTED THEFT')


#Create a variable crime_type the same size of the LA subset dataset
crime_type <- as.character(la_subset_positive$CrmCd.Desc)

#Loop through the entire dataset and identify crime type for each row in the dataset
i <- 1
while (i <= length(la_subset_positive$CrmCd.Desc)) 
{ crime_type[i] = 
    if(la_subset_positive$CrmCd.Desc[i] %in% physical_threat) {('physical_threat')}
    else if(la_subset_positive$CrmCd.Desc[i] %in% sexual_crimes) {('sexual_crime')}     
    else if(la_subset_positive$CrmCd.Desc[i] %in% theft_crimes) {('theft_crime')} 
    else {('other')}                                                        				      
    i = i + 1										      
}

#Create a new column in the dataset, crime_type to classify the type of crime
la_subset_positive$crime_type <- crime_type
head(la_subset_positive)

#Independent variables: Date.Rptd, DATE.OCC, TIME.OCC, AREA.NAME, CrmCd.Desc, crime_type, monthRptd and yearRptd
#Dependent variables: days_in_diff, Status.Desc
#Variables excluded from dataset:  Rd, Location, Cross.Street, Status, Location.1
#1-D analysis
#2-D analysis

par(mfrow = c(1, 1), mar = c(5, 4, 2, 1))
hist(la_subset_positive$days_in_diff, xlim=c(0,1422), breaks = 140)
hist(la_subset_positive$TIME.OCC)

reported_crimes_2012 <- subset(la_subset_positive, la_subset_positive$yearRptd == 12)
reported_crimes_2013 <- subset(la_subset_positive, la_subset_positive$yearRptd == 13)
reported_crimes_2014 <- subset(la_subset_positive, la_subset_positive$yearRptd == 14)
reported_crimes_2015 <- subset(la_subset_positive, la_subset_positive$yearRptd == 15)

crime_summary_by_area <- table(la_subset_positive$AREA.NAME)
barplot(crime_summary_by_area, main="Crimes in different areas of LA",names.arg=c("77th Street","Central", "Devonshire","Foothill", "Harbor", "Hollenbeck", "Hollywood","Mission N", "Hollywood", "Newton", "Northeast", "Olympic", "Pacific", "Rampart", "Southeast", "Southwest", "Topanga", "Van Nuys", "West LA", "West Valley", "Wilshire"))

par(mfrow = c(2, 2), mar = c(5, 4, 2, 1))

hist(reported_crimes_2012$days_in_diff, main="Difference in days in 2012", xlim=c(0,100), breaks=50)
hist(reported_crimes_2013$days_in_diff, main="Difference in days in 2013", xlim=c(0,100), breaks=50)
hist(reported_crimes_2014$days_in_diff, main="Difference in days in 2014", xlim=c(0,100), breaks=50)
hist(reported_crimes_2015$days_in_diff, main="Difference in days in 2015", xlim=c(0,100), breaks=50)

crime_summary_by_area_2012 <- table(reported_crimes_2012$AREA.NAME)
crime_summary_by_area_2013 <- table(reported_crimes_2013$AREA.NAME)
crime_summary_by_area_2014 <- table(reported_crimes_2014$AREA.NAME)
crime_summary_by_area_2015 <- table(reported_crimes_2015$AREA.NAME)

par(mfrow = c(2, 2), mar = c(5, 4, 2, 1))
barplot(crime_summary_by_area_2012, main="Crimes in different areas of LA in 2012",names.arg=c("77th Street","Central", "Devonshire","Foothill", "Harbor", "Hollenbeck", "Hollywood","Mission N", "Hollywood", "Newton", "Northeast", "Olympic", "Pacific", "Rampart", "Southeast", "Southwest", "Topanga", "Van Nuys", "West LA", "West Valley", "Wilshire"))
barplot(crime_summary_by_area_2013, main="Crimes in different areas of LA in 2013",names.arg=c("77th Street","Central", "Devonshire","Foothill", "Harbor", "Hollenbeck", "Hollywood","Mission N", "Hollywood", "Newton", "Northeast", "Olympic", "Pacific", "Rampart", "Southeast", "Southwest", "Topanga", "Van Nuys", "West LA", "West Valley", "Wilshire"))
barplot(crime_summary_by_area_2014, main="Crimes in different areas of LA in 2014",names.arg=c("77th Street","Central", "Devonshire","Foothill", "Harbor", "Hollenbeck", "Hollywood","Mission N", "Hollywood", "Newton", "Northeast", "Olympic", "Pacific", "Rampart", "Southeast", "Southwest", "Topanga", "Van Nuys", "West LA", "West Valley", "Wilshire"))
barplot(crime_summary_by_area_2015, main="Crimes in different areas of LA in 2015",names.arg=c("77th Street","Central", "Devonshire","Foothill", "Harbor", "Hollenbeck", "Hollywood","Mission N", "Hollywood", "Newton", "Northeast", "Olympic", "Pacific", "Rampart", "Southeast", "Southwest", "Topanga", "Van Nuys", "West LA", "West Valley", "Wilshire"))

hist(reported_crimes_2012$TIME.OCC, main="Crime time distribution in 2012")
hist(reported_crimes_2013$TIME.OCC, main="Crime time distribution in 2013")
hist(reported_crimes_2014$TIME.OCC, main="Crime time distribution in 2014")
hist(reported_crimes_2015$TIME.OCC, main="Crime time distribution in 2015")

plot(la_subset_positive$days_in_diff, la_subset_positive$TIME.OCC)
model <- lm(TIME.OCC ~ days_in_diff, la_subset_positive)
abline (model, lwd=6, col="red")

#Determine the number of crimes which have a pending status. Status of "Invest Cont"
investigation_continued <- subset(la_subset_positive, la_subset_positive$Status.Desc=="Invest Cont")
#use the variable above to perform analysis on crimes which have Investigation Continued status and see if there are any unique trends there

#look at distribution of crimes per month
#Distribution of crimes per Location (RD is the RD number, which you can use)
#Analysis of intersection which have highest crimes (combination of LOCATION and Cross.Street columns). This might need removing 
#Can extract latitude and longitude into 2 separate columns. And then make a plot of how that looks like. To see distribution of crimes by over a map area of sorts.
#Histogram of crimes by area and year
#Histogram of crimes by area and year+month
#You could also explore this http://www.milanor.net/blog/maps-in-r-plotting-data-points-on-a-map/
#also this https://sarahleejane.github.io/learning/r/2014/09/21/plotting-data-points-on-maps-with-r.html