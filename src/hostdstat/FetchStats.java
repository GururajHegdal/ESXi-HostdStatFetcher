/**
 * Utility class to fetch hostd MEM, Threads, FD, Responsiveness stats
 *
 * Copyright (c) 2016
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * @author Gururaja Hegdal (ghegdal@vmware.com)
 * @version 1.0
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package hostdstat;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;

import ch.ethz.ssh2.Connection;

public class FetchStats
{
    private String vsphereIp;
    private String userName;
    private String password;
    private String esx_username;
    private String esx_password;
    private String url;
    private ServiceInstance si;
    private String hostdMemUsage;
    private String hostdMemLimit;
    private String fdUsage;
    private String fdLimit;
    private String threadUsage;
    private String threadLimit;
    private String MEM_ALERT;
    private String THREAD_ALERT;
    private String FD_ALERT;
    private String RESPONSE_ALERT;

    // VC inventory related objects
    public static final String DC_MOR_TYPE = "Datacenter";
    public static final String CLUSTER_COMPRES_MOR_TYPE = "ClusterComputeResource";
    public static final String VC_ROOT_TYPE = "VCRoot";
    public static final String HOST_MOR_TYPE = "HostSystem";
    public static final String VM_MOR_TYPE = "VirtualMachine";
    public static final String MANAGEDENTITY_PARENT_PROPERTYNAME = "parent";
    private boolean ALERT_MEMORY_USAGE = false;
    private boolean hostdResponsive = true;

    /**
     * Constructor
     */
    public FetchStats(String[] cmdProps)
    {
        makeProperties(cmdProps);
    }

    /**
     * Read properties from command line arguments
     */
    private void
    makeProperties(String[] cmdProps)
    {
        // get the property value and print it out
        System.out.println("Reading vSphere IP and Credentials information from command line arguments");
        System.out.println("-------------------------------------------------------------------");

        for (int i = 0; i < cmdProps.length; i++) {
            if (cmdProps[i].equals("--vsphereip")) {
                vsphereIp = cmdProps[i + 1];
                System.out.println("vSphere IP:" + vsphereIp);
            } else if (cmdProps[i].equals("--username")) {
                userName = cmdProps[i + 1];
                System.out.println("VC Username:" + userName);
            } else if (cmdProps[i].equals("--password")) {
                password = cmdProps[i + 1];
                System.out.println("VC password: ******");
            }  else if (cmdProps[i].equals("--esxUsername")) {
                esx_username = cmdProps[i + 1];
                System.out.println("ESXi Username:" + userName);
            } else if (cmdProps[i].equals("--esxPassword")) {
                esx_password = cmdProps[i + 1];
                System.out.println("ESXi Password: ******");
            }
        }
        System.out.println("-------------------------------------------------------------------\n");
    }

    /**
     * Validate property values
     */
    boolean
    validateProperties()
    {
        boolean val = false;
        if (vsphereIp != null) {
            url = "https://" + vsphereIp + "/sdk";

            // Login to provided server IP to determine if we are running against single ESXi
            try {
                System.out.println("Logging into vSphere : " + vsphereIp + ", with provided credentials");
                si = loginTovSphere(url);

                if (si != null) {
                    System.out.println("Succesfully logged into vSphere: " + vsphereIp);
                    val = true;
                } else {
                    System.err.println(
                        "Service Instance object for vSphere:" + vsphereIp + " is null, probably we failed to login");
                    printFailedLoginReasons();
                }
            } catch (Exception e) {
                System.err.println(
                    "Caught an exception, while logging into vSphere :" + vsphereIp + " with provided credentials");
                printFailedLoginReasons();
            }
        }
        return val;
    }

    /**
     * Method prints out possible reasons for failed login
     */
    private void
    printFailedLoginReasons()
    {
        System.err.println(
            "Possible reasons:\n1. Provided username/password credentials are incorrect\n"
                + "2. If username/password or other fields contain special characters, surround them with double "
                + "quotes and for non-windows environment with single quotes (Refer readme doc for more information)\n"
                + "3. vCenter Server/ESXi server might not be reachable");
    }

    /**
     * Login method to VC
     */
    private ServiceInstance
    loginTovSphere(String url)
    {
        try {
            si = new ServiceInstance(new URL(url), userName, password, true);
        } catch (Exception e) {
            System.out.println("Caught exception while logging into vSphere server");
            e.printStackTrace();
        }
        return si;
    }

    /**
     * Fetch all Stats method
     */
    public void
    fetchHostdStats()
    {
        String tempHostName = null;
        System.out.println("Retrieving all hosts from VC ...");
        ManagedEntity[] allHosts = retrieveAllHosts();

        if (allHosts != null) {
            Connection sshConn = null;
            for (ManagedEntity host : allHosts) {

                try {
                    tempHostName = host.getName();
                    System.out.println(
                        "\n******************************************************************************");
                    System.out.println("\t\t\tHost : " + tempHostName);
                    System.out.println(
                        "******************************************************************************");
                    // Get SSHConnection
                    sshConn = SSHUtil.getSSHConnection(tempHostName, esx_username,
                        esx_password);
                    if (sshConn != null) {
                        System.out.println("\n*** About to retrieve hostd MEMORY information ...");
                        memoryResourceChecker(sshConn);
                        System.out.println("\n*** About to retrieve hostd THREAD information ...");
                        threadResourceChecker(sshConn);
                        System.out.println("\n*** About to retrieve hostd FD information ...");
                        FDResourceChecker(sshConn);
                        System.out.println("\n*** About to retrieve hostd RESPONSIVENESS information ...");
                        responseChecker(sshConn);
                    } else {
                        System.err.println("Caught exception while fetching SSH Connection object");
                    }

                } catch (Exception e) {
                    System.err.println("Caught exception while fetching stats from host: " + tempHostName);
                } finally {
                    if (sshConn != null) {
                        sshConn.close();
                    }
                }

                // Report the stats
                System.out.println("\n^^^^^^^^^^^^^^^^^   S T A T S   ^^^^^^^^^^^^^^^^^");
                try {
                    if (hostdMemUsage != null && hostdMemLimit != null) {
                        System.out.println("* MEMORY:");
                        System.out.println("--- Usage:" + hostdMemUsage + " MB, Limit:"
                                 + hostdMemLimit + " MB");
                        // memory threshold checker
                        Float tempMemUsage = Float.parseFloat(hostdMemUsage);
                        Float tempMemLimit = Float.parseFloat(hostdMemLimit);
                        Float currMemThreshold = (((tempMemUsage) / tempMemLimit)) * 100;
                        if (currMemThreshold >= 95) {
                           MEM_ALERT = "RED";
                        } else if (currMemThreshold >= 85 && currMemThreshold < 95) {
                           MEM_ALERT = "WARNING";
                        } else if (currMemThreshold < 85) {
                           MEM_ALERT = "GREEN";
                        }
                        System.out.println("--- Threshold: " + currMemThreshold
                                 + "%, ALERT:" + MEM_ALERT);
                     }
                } catch (Exception e) {
                    System.err.println("Caught exception while reporting Memory stats");
                }

                try {
                    if (threadLimit != null && threadUsage != null) {
                        //Thread usage can be empty
                        if (threadUsage.equals("")) {
                            threadUsage = "0";
                        }
                        System.out.println("* THREAD:");
                        System.out.println("--- Usage:" + threadUsage + ", Limit:"
                                 + threadLimit);
                        // thread threshold checker
                        Float tempThreadLimit = Float.parseFloat(threadLimit);
                        Float tempThreadUsage = Float.parseFloat(threadUsage);

                        Float currThreadThreshold = ((((tempThreadUsage) / tempThreadLimit)) * 100);
                        if (currThreadThreshold >= 95) {
                           THREAD_ALERT = "RED";
                        } else if (currThreadThreshold >= 85 && currThreadThreshold < 95) {
                           THREAD_ALERT = "WARNING";
                        } else if (currThreadThreshold < 85) {
                           THREAD_ALERT = "GREEN";
                        }
                        System.out.println("--- Threshold: " + currThreadThreshold
                                 + "%, ALERT:" + THREAD_ALERT);
                     }

                } catch (Exception e) {
                    System.err.println("Caught exception while reporting Thread stats");
                }

                try {
                    if (fdLimit != null && fdUsage != null) {
                        System.out.println("* FD:");
                        System.out.println("--- Usage:" + fdUsage + ", Limit:" + fdLimit);
                        // FD threshold checker
                        Float tempFdLimit = Float.parseFloat(fdLimit);
                        Float tempFdUsage = Float.parseFloat(fdUsage);

                        Float currFdThreshold = ((((tempFdUsage) / tempFdLimit)) * 100);
                        if (currFdThreshold >= 95) {
                           FD_ALERT = "RED";
                        } else if (currFdThreshold >= 85 && currFdThreshold < 95) {
                           FD_ALERT = "WARNING";
                        } else if (currFdThreshold < 85) {
                           FD_ALERT = "GREEN";
                        }
                        System.out.println("--- Threshold: " + currFdThreshold + "%, ALERT:"
                                 + FD_ALERT);
                     }

                } catch (Exception e) {
                    System.err.println("Caught exception while reporting FD stats");
                }

                System.out.println("* RESPONSIVENESS:");
                 System.out.println("--- Hostd responsive:" + hostdResponsive
                          + ", RESPONSE ALERT:" + RESPONSE_ALERT);

            } // End of hosts for loop

        } else {
            System.err.println("Could not find any hosts in inventory");
        }
    }

    /**
     * Get All hosts
     */
    private ManagedEntity[]
    retrieveAllHosts()
    {
        // get first datacenters in the environment.
        InventoryNavigator navigator = new InventoryNavigator(si.getRootFolder());
        ManagedEntity[] hosts = null;
        try {
            hosts = navigator.searchManagedEntities(HOST_MOR_TYPE);
        } catch (Exception e) {
            System.err.println("[Error] Unable to retrive Hosts from inventory");
            e.printStackTrace();
        }
        return hosts;
    }

    /**
     * memory checker
     */
    private void
    memoryResourceChecker(Connection sshConn) throws Exception
    {
       /*
        * Memory Usage
        */
       String memCurrUsageCmd = "esxcfg-resgrp -l host/vim/vmvisor/hostd |"
                + "grep -E \"Group Name|Effective Minimum\" |"
                + "grep -E \"hostd.[0-9]+\" -A 2 |" + "grep -o -E "
                + "\"[0-9]+\\" + "." + "[0-9]* MB\"";

       Map<String, String> memUsageMap = SSHUtil.getRemoteSSHCmdOutput(sshConn,
                memCurrUsageCmd);

       List<Boolean> errorStream = null;
       for (String key : memUsageMap.keySet()) {
          if (key.equals(SSHUtil.SSH_ERROR_STREAM)) {
             errorStream = new ArrayList<Boolean>();
             System.out.println("memusage:" + memUsageMap.get(key));

             if (!(memUsageMap.get(key).equals(""))) {
                System.out.println("[SSHErrorStream-Usage] Error in executing the command");
                errorStream.add(true);
                break;
             } else {
                errorStream.add(false);
             }
          }
       }

       for (String key : memUsageMap.keySet()) {
          if (key.equals(SSHUtil.SSH_OUTPUT_STREAM)) {
             if (errorStream != null && errorStream.get(0).equals(false)) {
                hostdMemUsage = memUsageMap.get(key);
             }

          }
       }

       /*
        * Memory Limit
        */
       String memLimitCmd = "esxcfg-resgrp -l host/vim/vmvisor/hostd | "
                + "grep \"Group Capacity\" -A 4 | " + "grep \"Total Memory\" | "
                + "head -n 1 | " + "grep -o -E " + "\"[0-9]+\\" + "."
                + "[0-9]* MB\"";

       Map<String, String> memLimitMap = SSHUtil.getRemoteSSHCmdOutput(sshConn,
                memLimitCmd);

       errorStream = null;
       for (String key : memLimitMap.keySet()) {
          if (key.equals(SSHUtil.SSH_ERROR_STREAM)) {
             errorStream = new ArrayList<Boolean>();
             if (!(memLimitMap.get(key).equals(""))) {
                System.out.println("[SSHErrorStream-Limit] Error in executing the command");
                errorStream.add(true);
                break;
             } else {
                errorStream.add(false);
             }
          }
       }

       for (String key : memLimitMap.keySet()) {
          if (key.equals(SSHUtil.SSH_OUTPUT_STREAM)) {
             if (errorStream != null && errorStream.get(0).equals(false)) {
                hostdMemLimit = memLimitMap.get(key);
             }

          }
       }

       hostdMemUsage = hostdMemUsage.substring(0, hostdMemUsage.indexOf("MB")).trim();
       hostdMemLimit = hostdMemLimit.substring(0, hostdMemLimit.indexOf("MB")).trim();

       if (hostdMemUsage.equals(hostdMemLimit)) {
          ALERT_MEMORY_USAGE = true;
       }

    }

    /**
     * Thread resource checker
     */
    private void
    threadResourceChecker(Connection sshConn) throws Exception
    {
        /*
         * Thread usage
         */
       String threadCurrUsageCmd = "grep \"HandleWork(type:\" /var/log/hostd.log |"
                + " tail -n 1 | "
                + " grep -o -E \"busy_long:[0-9]+\" |"
                + " grep -o -E  \"[0-9]\"";

       Map<String, String> threadUsageMap = SSHUtil.getRemoteSSHCmdOutput(
                sshConn, threadCurrUsageCmd);

       List<Boolean> errorStream = null;
       for (String key : threadUsageMap.keySet()) {
          if (key.equals(SSHUtil.SSH_ERROR_STREAM)) {
             errorStream = new ArrayList<Boolean>();
             System.out.println("threadusage:" + threadUsageMap.get(key));

             if (!(threadUsageMap.get(key).equals(""))) {
                System.out.println("[SSHErrorStream-ThreadUsage] Error in executing the command");
                errorStream.add(true);
                break;
             } else {
                errorStream.add(false);
             }
          }
       }

       for (String key : threadUsageMap.keySet()) {
          if (key.equals(SSHUtil.SSH_OUTPUT_STREAM)) {
             if (errorStream != null && errorStream.get(0).equals(false)) {
                threadUsage = threadUsageMap.get(key);
             }

          }
       }

       /*
        * Thread Limit
        */
       String threadLimitCmd = "grep \"<TaskMax>\" /etc/vmware/hostd/config.xml | "
                + "grep -o -E \"[0-9]+\"";

       Map<String, String> threadLimitMap = SSHUtil.getRemoteSSHCmdOutput(
                sshConn, threadLimitCmd);

       errorStream = null;
       for (String key : threadLimitMap.keySet()) {
          if (key.equals(SSHUtil.SSH_ERROR_STREAM)) {
             errorStream = new ArrayList<Boolean>();
             System.out.println("threadlimit:" + threadLimitMap.get(key));

             if (!(threadLimitMap.get(key).equals(""))) {
                System.out.println("[SSHErrorStream-ThreadLimit] Error in executing the command");
                errorStream.add(true);
                break;
             } else {
                errorStream.add(false);
             }
          }
       }

       for (String key : threadLimitMap.keySet()) {
          if (key.equals(SSHUtil.SSH_OUTPUT_STREAM)) {
             if (errorStream != null && errorStream.get(0).equals(false)) {
                threadLimit = threadLimitMap.get(key);
             }

          }
       }

       threadUsage = threadUsage.replace("\n", "");
       threadLimit = threadLimit.replace("\n", "");

    }

    /**
     * File Descriptor resource checker
     */
    private void
    FDResourceChecker(Connection sshConn) throws Exception
    {
        /*
         * FD current usage
         */
       String fdCurrUsageCmd = "vmkvsitools lsof | grep hostd-worker | wc -l";

       Map<String, String> fdUsageMap = SSHUtil.getRemoteSSHCmdOutput(sshConn,
                fdCurrUsageCmd);

       List<Boolean> errorStream = null;
       for (String key : fdUsageMap.keySet()) {
          if (key.equals(SSHUtil.SSH_ERROR_STREAM)) {
             errorStream = new ArrayList<Boolean>();
             System.out.println("FDusage:" + fdUsageMap.get(key));

             if (!(fdUsageMap.get(key).equals(""))) {
                System.out.println("[SSHErrorStream-FDUsage] Error in executing the command");
                errorStream.add(true);
                break;
             } else {
                errorStream.add(false);
             }
          }
       }

       for (String key : fdUsageMap.keySet()) {
          if (key.equals(SSHUtil.SSH_OUTPUT_STREAM)) {
             if (errorStream != null && errorStream.get(0).equals(false)) {
                fdUsage = fdUsageMap.get(key);
             }

          }
       }

       /*
        * FD Limit
        */
       String fdLimitCmd = "Base=`grep \"<hostdMinFds>\" /etc/vmware/hostd/config.xml | "
                + "grep -o -E \"[0-9]+\"`; "
                + "SupportedVMs=`vsish -e get /system/supportedVMs`; "
                + "Limit=$(expr $Base + $SupportedVMs \\* 2); " + "echo $Limit";

       Map<String, String> fdLimitMap = SSHUtil.getRemoteSSHCmdOutput(sshConn,
                fdLimitCmd);

       errorStream = null;
       for (String key : fdLimitMap.keySet()) {
          if (key.equals(SSHUtil.SSH_ERROR_STREAM)) {
             errorStream = new ArrayList<Boolean>();
             System.out.println("fdlimit:" + fdLimitMap.get(key));

             if (!(fdLimitMap.get(key).equals(""))) {
                System.out.println("[SSHErrorStream-FDLimit] Error in executing the command");
                errorStream.add(true);
                break;
             } else {
                errorStream.add(false);
             }
          }
       }

       for (String key : fdLimitMap.keySet()) {
          if (key.equals(SSHUtil.SSH_OUTPUT_STREAM)) {
             if (errorStream != null && errorStream.get(0).equals(false)) {
                fdLimit = fdLimitMap.get(key);
             }

          }
       }

       fdUsage = fdUsage.replace("\n", "");
       fdLimit = fdLimit.replace("\n", "");

    }

    /**
     * Response checker
     */
    private void
    responseChecker(Connection sshConn) throws Exception
    {
       String respCheckerCmd = "grep \"hostd detected to be non-responsive\" /var/log/hostd-probe.log";

       Map<String, String> respChkerMap = SSHUtil.getRemoteSSHCmdOutput(sshConn,
                respCheckerCmd);

       List<Boolean> errorStream = null;
       for (String key : respChkerMap.keySet()) {
          if (key.equals(SSHUtil.SSH_ERROR_STREAM)) {
             errorStream = new ArrayList<Boolean>();
             //System.out.println("RespChecker:" + respChkerMap.get(key));

             if (!(respChkerMap.get(key).equals(""))) {
                System.out.println("[SSHErrorStream-RespChecker] Error in executing the command");
                errorStream.add(true);
                break;
             } else {
                errorStream.add(false);
             }
          }
       }

       for (String key : respChkerMap.keySet()) {
          if (key.equals(SSHUtil.SSH_OUTPUT_STREAM)) {
             if (errorStream != null && errorStream.get(0).equals(false)) {
                if (!(respChkerMap.get(key).equals(""))) {
                   hostdResponsive = false;
                   RESPONSE_ALERT = "RED";
                } else {
                   RESPONSE_ALERT = "GREEN";
                }
             }

          }
       }
    }
}