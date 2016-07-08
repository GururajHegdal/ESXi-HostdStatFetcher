/**
 * Utility program to handle SSH Connection to ESXi hosts, running commands,
 * on VMware ESXi host
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
 * @author VMware
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.ethz.ssh2.ChannelCondition;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.InteractiveCallback;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

public class SSHUtil
{
    public static final long SSHCOMMAND_TIMEOUT = 300;
    public static final String SSH_ERROR_STREAM = "SSHErrorStream";
    public static final String SSH_OUTPUT_STREAM = "SSHOutputStream";
    public static final String SSH_EXIT_CODE = "SSHExitCode";
    public static final String SERVICE_STATE_RUNNING = "RUNNING";
    public static final String SERVICE_STATE_NOT_RUNNING = "NOT RUNNING";
    public static final String SERVICE_STATE_STOPPED = "STOPPED";

    /**
     * Connects to the remote host using SSH
     *
     * @param hostName host to connect
     * @param userName username to authenticate
     * @param password password to authenticate
     * @return SSH Connection
     * @throws Exception
     */
    public static Connection
    getSSHConnection(String hostName, String userName, final String password) throws Exception
    {
        Connection conn = new Connection(hostName);
        String[] strArray;
        // Now try to connect
        conn.connect();

        try {
            strArray = conn.getRemainingAuthMethods(userName);
        } catch (IOException e) {
            throw new Exception("Getting Remaining AuthMethods failed with IOException: " + e.getMessage());
        }
        if (strArray == null) {
            System.out.println("conn.getRemainingAuthMethods returns null");
            try {
                conn.authenticateWithPassword(userName, password);
            } catch (Exception e) {
                String warning = "";
                if (password.equals("")) {
                    warning += " : " + "Warning: Implementation of this package "
                        + "does not allow empty passwords for authentication";
                }
                throw new Exception("Authentication with password failed: " + e.getMessage() + warning);
            }
        } else {
            List<String> authMethods = Arrays.asList(strArray);
            // Authenticate
            if (authMethods.contains("password")) {
                if (!conn.authenticateWithPassword(userName, password)) {
                    throw new Exception("Password based authentication failed.");
                }
            } else if (authMethods.contains("keyboard-interactive")) {
                InteractiveCallback cb = new InteractiveCallback() {
                    @Override
                    public String[] replyToChallenge(String name, String instruction, int numPrompts, String[] prompt,
                        boolean[] echo) throws Exception {
                        /*
                         * Going with the assumption that the only thing servers
                         * asks for is password
                         */
                        String[] response = new String[numPrompts];
                        for (int i = 0; i < response.length; i++) {
                            response[i] = password;
                        }
                        return response;
                    }
                };
                if (!conn.authenticateWithKeyboardInteractive(userName, cb)) {
                    throw new Exception("Keyboard-interactive based authentication failed.");
                }
            } else {
                throw new Exception("SSH Server doesnt support password or keyboard-interactive logins");
            }
        }
        System.out.println("Successfully connected to the remote ssh host: " + hostName);
        return conn;
    }

    /**
     * Closes the SSH Connection to the remote host
     *
     * @param conn SSH Connection
     * @return true if successful, exception raised otherwise
     * @throws Exception
     */
    public static boolean
    closeSSHConnection(Connection conn) throws Exception
    {
        boolean success = true;
        if (conn != null) {
            conn.close();
            System.out.println("SSH Connection closed");
        }
        return success;
    }

    /**
     * Executes the given command on the remote host using ssh
     *
     * @param conn SSH Connection
     * @param command Command to be executed
     * @param timeout Timeout in seconds
     * @return HashMap with both error and output stream contents
     * @throws IOException , Exception
     */
    public static Map<String, String>
    getRemoteSSHCmdOutput(Connection conn, String command, long timeout) throws Exception
    {
        Session session = null;
        InputStream stderr = null;
        InputStream stdout = null;
        Map<String, String> returnData = new HashMap<String, String>();
        try {
            session = conn.openSession();
            System.out.println("Running command '" + command + "' with timeout of " + timeout + " seconds");
            session.execCommand(command);
            // Wait until command completes or times out
            int result = session.waitForCondition(ChannelCondition.EOF, timeout * 1000);
            if ((result & ChannelCondition.TIMEOUT) != 0) {
                System.out.println("A timeout occured while waiting for data from the " + "server");
                if (session != null) {
                    session.close();
                }
                return returnData;
            }
            stderr = new StreamGobbler(session.getStderr());
            stdout = new StreamGobbler(session.getStdout());
            // populate output stream
            StringBuffer outputDataStream = getInputStreamString(stdout);
            returnData.put(SSH_OUTPUT_STREAM, outputDataStream.toString());
            // populate error stream
            StringBuffer errorDataStream = getInputStreamString(stderr);
            returnData.put(SSH_ERROR_STREAM, errorDataStream.toString());
            Integer exitStatus = session.getExitStatus();
            if (errorDataStream.length() != 0) {
                // command execution failed ( even if execution of one command fails)
                System.err.println("SSH session ExitCode: " + exitStatus);
                System.err.println("Error while executing '" + command + "' command on remote ssh host");
                System.err.println("Error Stream: \n" + errorDataStream);
                System.out.println("Output Stream: \n" + outputDataStream);
            } else {
                // command executed successfully , populate the output stream
                System.out.println("SSH session ExitCode: " + exitStatus);
                System.out.println("Successfully executed '" + command + "' command on remote ssh host");
            }
        } finally {
            if (session != null) {
                session.close();
            }
            if (stderr != null) {
                stderr.close();
            }
            if (stdout != null) {
                stdout.close();
            }
        }
        // returnData must contain Error as well as output stream
        // and the test cases would decide accordingly
        return returnData;
    }

    /**
     * Populate a StringBuffer with the contents of an InputStream
     *
     * @param out InputStream to process
     * @return StringBuffer object containing InputStream contents
     * @throws IOException
     */
    public static StringBuffer
    getInputStreamString(final InputStream in) throws Exception
    {
        StringBuffer out = null;
        if (in != null) {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            try {
                out = new StringBuffer();
                String tmp = "";
                while ((tmp = reader.readLine()) != null) {
                    out.append(tmp + "\n");
                }
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        } else {
            System.err.println("InputStream parameter is null");
        }
        return out;
    }

    /**
     * Method to execute and return command output, without having to specify timeout.
     * default timeout is considered.
     */
    public static Map<String, String>
    getRemoteSSHCmdOutput(Connection conn, String command) throws Exception
    {
        return getRemoteSSHCmdOutput(conn, command, SSHCOMMAND_TIMEOUT);
    }

    /**
     * Asynchronously executes the given command on the remote host using ssh. It
     * doesn't waits for command to complete on the remote host.
     *
     * @param Connection SSH Connection
     * @param command Command to be executed
     * @throws IOException , Exception
     */
    public static void
    executeAsyncRemoteSSHCommand(Connection conn, String command) throws Exception
    {
        Session session = null;
        try {
            session = conn.openSession();
            System.out.println(
                "Running command '" + command + "' asynchronously. "
                    + " It doesn't wait for command to complete on remote host.");
            session.execCommand(command);
            int sleep = 10;
            System.out.println("Sleep for " + sleep + " seconds for command to kick in.");
            Thread.sleep(sleep * 1000);

        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    /**
     * Executes the given command on the remote host using ssh
     *
     * @param session session of a SSH Connection
     * @param command Command to be executed
     * @param maxTimeout The max number of seconds to wait for the command to
     *            execute
     * @return true, if successful, false, otherwise
     * @throws IOException , Exception
     */
    public static boolean
    executeRemoteSSHCommand(Session session, String command, long maxTimeout) throws Exception
    {
        StreamReader isReader = null;
        StreamReader errReader = null;
        String errorDataStream = null;
        boolean success = false;

        try {
            System.out.println("Running command '" + command + "' with timeout of " + maxTimeout + " seconds");
            session.execCommand(command);

            /*
             * Start stream reader for standard output
             */
            isReader = new StreamReader(new BufferedReader(new InputStreamReader(session.getStdout())), "InputStream");

            Thread outThread = new Thread(isReader);
            outThread.start();

            /*
             * Start stream reader for error stream
             */
            errReader = new StreamReader(new BufferedReader(new InputStreamReader(session.getStderr())), "ErrorStream");
            Thread errThread = new Thread(errReader);
            errThread.start();

            /*
             * Wait until command completes or times out
             */
            int result = session.waitForCondition(ChannelCondition.EOF, maxTimeout * 1000);
            if ((result & ChannelCondition.TIMEOUT) != 0) {
                System.out.println("A timeout occured while waiting for data from the " + "server");
            } else {
                /*
                 * It is possible that the errReader thread has not completely
                 * finished saving error data at this point, since
                 * waitForCondition and errReader are running concurrently.
                 * Sleep 2 seconds to allow errReader thread extra time to
                 * finish.
                 */
                Thread.sleep(2000);
                errorDataStream = errReader.getDataStream();
                if ((errorDataStream == null || errorDataStream.length() == 0)) {
                    /*
                     * Some server implementations do not return an exit status
                     */
                    Integer exitStatus = session.getExitStatus();
                    if (exitStatus == null) {
                        System.out.println("'" + command + "' command did not return an " + "exit status value");
                        success = true;
                    } else {
                        /*
                         * Nonzero exit status value is an error
                         */
                        System.out
                            .println("'" + command + "' command returned an exit " + "status value: " + exitStatus);
                        if (exitStatus.equals(0)) {
                            success = true;
                        } else {
                            System.out.println("'" + command + "' command returned a nonzero " + "exit status value");
                        }
                    }
                } else {
                    System.out.println("Error data stream contains a message");
                    if (errorDataStream.contains("Terminating watchdog process")
                        || errorDataStream.contains("Picked up JAVA_TOOL_OPTIONS:")) {
                        // ignore this error mesg.
                        success = true;
                    }

                    String output = isReader.getDataStream();
                }
            }
            if (success) {
                System.out.println("Successfully executed '" + command + "' command on remote ssh host");
            }
        } finally {
            if (isReader != null) {
                isReader.stopThread();
            }
            if (errReader != null) {
                errReader.stopThread();
            }
        }
        return success;
    }
}