/**
 * Utility Class to retrieve ESXi Hostd Service Stats such as
 * -- Memory usage and Limit
 * -- Threads Usage and Limit
 * -- FD Usage and Limit
 * -- Responsiveness
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

// Entry point into the Hostd Stats fetcher tool
public class RunApp
{
    /**
     * Usage method - how to use/invoke the script, reveals the options supported through this script
     */
    public static void usageHostdStatScript()
    {
        System.out.println(
            "Usage: java -jar hostdstat.jar --vsphereip <vc/esxi server IP> --username <uname> --password <pwd> --esxUsername <uname> --esxPassword <pwd>");
        System.out.println(
            "\"java -jar hostdstat.jar --vsphereip 10.4.5.6 --username admin --password dummyPwd --esxUsername rootUser --esxPassword dummyPwd\"");
     }

    /**
     * Main entry point
     */
    public static void main(String[] args) {

        System.out
            .println("######################### Hostd Stats fetcher Configuration Script execution STARTED #########################");

        // Read command line arguments
        if (args.length > 0 && args.length >= 10) {
            FetchStats fetchStatObj = new FetchStats(args);
            if (fetchStatObj.validateProperties()) {
                fetchStatObj.fetchHostdStats();
            } else {
                usageHostdStatScript();
            }
        } else {
            usageHostdStatScript();
        }
        try {
            Thread.sleep(1000 * 2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(
            "######################### Hostd Stats fetcher Script execution completed #########################");
    }
}