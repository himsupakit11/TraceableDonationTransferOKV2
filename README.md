- In this version include all API e.g. campaign creation(run by fundraiser), making a donation(run by donor), and transfering fund to receipient(run by fundraiser).
- All API ok
- Endcampaign is OK, transfers fund to fundraiser but transfers fund to recipient in progress
Deploy Node: PATH: TracebleDonation
    Commands: ./gradlew deployNodes
                 ./cordapp-contracts-states/build/nodes/runnodes
Deploy Webapp: PATH: TracebleDonation
    Commands:
            ./gradlew build -x test

     Fundraiser : java -jar clients/build/libs/clients-0.1.jar

     Bank :       java -jar -Dserver.port=8181 clients/build/libs/clients-0.1.jar

     Recipient :  java -jar -Dserver.port=8282 clients/build/libs/clients-0.1.jar

     Donor :      java -jar -Dserver.port=8383 clients/build/libs/clients-0.1.jar

Kill process:
    Commands: ps -ef | grep "corda"
              killall -9 java

              lsof -n -i4TCP:8080

See log: tail -f node-info-gen.log

API URL:
http://localhost:8080/api/campaigns/availableCampaign
http://localhost:8383/api/donations/
http://localhost:8080/api/receipts/
http://localhost:8080/api/campaigns/FinishedAndSuccessfulCampaign
http://localhost:8080/api/campaigns/FinishedAndFailedCampaign
http://localhost:8080/api/campaigns/consumedCampaign
http://localhost:8080/api/cash/cashState






Spring boot Fundraiser: java -jar /Users/hmosx/Desktop/KMUTT/4\ th/Semester2/Project/temp/Receipt/clients/build/libs/clients-0.1.jar
Spring boot Donor: java -jar -Dserver.port=8383 /Users/hmosx/Desktop/KMUTT/4\ th/Semester2/Project/temp/Receipt/clients/build/libs/clients-0.1.jar

Fundraiser log: cd /Users/hmosx/Desktop/KMUTT/4\ th/Semester2/Project/temp/Receipt/cordapp-contracts-states/build/nodes/Fundraiser/logs
Donor log: cd /Users/hmosx/Desktop/KMUTT/4\ th/Semester2/Project/temp/Receipt/cordapp-contracts-states/build/nodes/Donor/logs
Receipient: cd /Users/hmosx/Desktop/KMUTT/4\ th/Semester2/Project/temp/Receipt/cordapp-contracts-states/build/nodes/Recipient/logs


