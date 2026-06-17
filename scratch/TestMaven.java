package org.apache.maven.cli;

public class TestMaven {
    public static void main(String[] args) {
        System.setProperty("maven.home", "E:\\works\\.m2\\wrapper\\dists\\apache-maven-3.9.15-bin\\4rlcemksed9vjmkvgss0jpc4po\\apache-maven-3.9.15");
        System.setProperty("maven.multiModuleProjectDirectory", "E:\\works");
        System.setProperty("classworlds.conf", "E:\\works\\.m2\\wrapper\\dists\\apache-maven-3.9.15-bin\\4rlcemksed9vjmkvgss0jpc4po\\apache-maven-3.9.15\\bin\\m2.conf");
        System.setProperty("maven.conf", "E:\\works\\.m2\\wrapper\\dists\\apache-maven-3.9.15-bin\\4rlcemksed9vjmkvgss0jpc4po\\apache-maven-3.9.15\\conf");
        
        MavenCli.CliRequest cliRequest = new MavenCli.CliRequest(new String[]{"-version"}, null);
        cliRequest.workingDirectory = "E:\\works";
        
        MavenCli cli = new MavenCli();
        try {
            System.out.println("Calling initialize...");
            cli.initialize(cliRequest);
            System.out.println("Calling cli...");
            cli.cli(cliRequest);
            System.out.println("Calling logging...");
            cli.logging(cliRequest);
            System.out.println("Calling version...");
            cli.version(cliRequest);
            System.out.println("Calling properties...");
            cli.properties(cliRequest);
            System.out.println("Calling container...");
            cli.container(cliRequest);
            System.out.println("Success!");
        } catch (Throwable t) {
            System.out.println("EXCEPTION IN STEPS:");
            t.printStackTrace(System.out);
        }
    }
}
