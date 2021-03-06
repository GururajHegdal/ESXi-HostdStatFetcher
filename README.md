# VMware ESXi- Hostd Service StatFetcher
### 1. Details
Utility to retrieve ESXi Hostd Service Stats such as
 * Memory usage and Limit
 * Threads Usage and Limit
 * FD Usage and Limit
 * Responsiveness

Utility connects to provided vCenter Server IP with the credentials and retrieves all ESXi hosts that are currently managed. Then for each of the ESXi host, utility connects via SSH to run commands and retrieve HOSTD service stats. Once done SSH Connection is closed.
If SSH Service is disabled, using APIs utility enables SSH service on each ESXi host and once done, reverts SSH Service state (i.e stops the service).

### 2. How to run the Utility?
##### Run from Dev IDE

 * Import files under the src/hostdstat/ folder into your IDE.
 * Required libraries are embedded within Runnable-Jar/hostdstat.jar, extract & import the libraries into the project.
 *  Run the utility from 'RunApp' program by providing arguments like:  
 _--vsphereip 1.2.3.4 --username adminUser --password dummyPasswd --esxUsername rootUser --esxPassword rootPwd_

If the username and password for ESXi hosts differ, source code can easily be edited to include simple logic to fetch username/password per ESXi host.

##### Run from Pre-built Jars
 * Copy/Download the hostdstat.jar from Runnable-jar folder (from the uploaded file) and unzip on to local drive folder say c:\hostdstat
 * Open a command prompt and cd to the folder, lets say cd hostdstat
 * Run a command like shown below to see various usage commands:  
 _C:\hostdstat>java -jar hostdstat.jar --help_
 
### 3. Sample output
```
^^^^^^^^^^^^^^^^^   S T A T S   ^^^^^^^^^^^^^^^^^
* MEMORY:
--- Usage:63.79 MB, Limit:268.00 MB
--- Threshold: 23.802238%, ALERT:GREEN
* THREAD:
--- Usage:0, Limit:22
--- Threshold: 0.0%, ALERT:GREEN
* FD:
--- Usage:285, Limit:3126
--- Threshold: 9.117083%, ALERT:GREEN
* RESPONSIVENESS:
--- Hostd responsive:true, RESPONSE ALERT:GREEN
```
