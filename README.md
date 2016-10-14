
This project contains code for a Jenkins plugin that integrates with
Catalogic Software's [ECX](https://catalogicsoftware.com/products/ecx/)
product.

Before you prepare for Publishing the plugin into the marketplace make sure you follow the following steps:

1.The github account should be configured for ssh access. More details can be found at https://help.github.com/articles/generating-an-ssh-key/

2.Create file settings.xml in your home directory. The contents of the file are as follows:

<settings>
  <pluginGroups>
    <pluginGroup>org.jenkins-ci.tools</pluginGroup>
  </pluginGroups>

<servers>
    <server>
      <id>maven.jenkins-ci.org</id> <!- For parent 1.397 or newer; before this use id java.net-m2-repository ->
      <username>neethapai</username>
      <password>pwSKMHXHgdhskbcvdw8hsghag7d</password>
    </server>
  </servers>

  <profiles>
    <!- Give access to Jenkins plugins ->
    <profile>
      <id>jenkins</id>
      <activation>
        <activeByDefault>true</activeByDefault> <!- change this to false, if you don't like to have it on per default ->
      </activation>
      <repositories>
        <repository>
          <id>repo.jenkins-ci.org</id>
          <url>https://repo.jenkins-ci.org/public/</url>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>repo.jenkins-ci.org</id>
          <url>https://repo.jenkins-ci.org/public/</url>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <mirrors>
    <mirror>
      <id>repo.jenkins-ci.org</id>
      <url>https://repo.jenkins-ci.org/public/</url>
      <mirrorOf>m.g.o-public</mirrorOf>
    </mirror>
  </mirrors>
</settings>

3. Make sure you have logged into artifactory atleast once.

4. Replace "USERNAME" with your Jenkins Jira username and "PASSWORD" with the Jenkins Jira password in encypted format. To get the encrypted password follow these steps:
  a) Login https://repo.jenkins-ci.org/webapp/#/login with jenkins-ci.org account
  b) Go to https://repo.jenkins-ci.org/webapp/#/profile
  c) Unlock "Current Password"
  d) Add the "Encrypted Password" to your settings.xml file

5. Run the maven command as  mvn -X -s /home/user/settings.xml release:prepare release:perform
