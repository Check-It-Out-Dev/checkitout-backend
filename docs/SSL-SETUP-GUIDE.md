# 🔐 SSL/HTTPS Setup for Local Spring Boot Development

## 📋 Overview
This guide explains how to configure SSL/HTTPS for local Spring Boot development, which is **required for Instagram OAuth callbacks** to localhost. Meta/Instagram OAuth requires HTTPS even for localhost development.

## 🎯 Why SSL is Required
- Instagram OAuth **requires HTTPS** for all redirect URIs (including localhost)
- Browser security features work better with HTTPS
- Mimics production environment more closely
- Required for testing OAuth flow locally

## 🛠️ Prerequisites
- Java JDK installed (you already have this)
- Access to the backend repository
- IntelliJ IDEA or command line

## 📦 Step-by-Step Setup

### Step 1: Generate SSL Certificate
Since all developers have Java installed, we'll use the Java `keytool` command.

Open PowerShell or Command Prompt in the backend directory:
```bash
cd <repo-root>
```

Run this single command to generate the certificate:
```bash
keytool -genkeypair -alias tomcat -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 3650 -storepass changeit -dname "CN=localhost, OU=Development, O=CheckItOut, L=Warsaw, ST=Mazovia, C=PL"
```

### Step 2: Move Certificate to Resources
Move the generated `keystore.p12` file to the resources directory:
```bash
# Windows PowerShell
move keystore.p12 src\main\resources\

# Or manually move the file to:
# src/main/resources/keystore.p12
```

### Step 3: Verify Certificate
Check that the certificate was created correctly:
```bash
# List certificate details
keytool -list -v -keystore src\main\resources\keystore.p12 -storepass changeit -storetype PKCS12

# Or just check if file exists
dir src\main\resources\keystore.p12
```

Expected output: You should see a file of approximately 2.7KB size.

## 📝 SSL Configuration File

The SSL configuration is already created in `src/main/resources/application-ssl.yml`:

```yaml
# =============================================================================
# SSL CONFIGURATION FOR LOCAL DEVELOPMENT
# =============================================================================
# Usage: Include this profile when running locally with OAuth
# Example: mvn spring-boot:run -Dspring.profiles.active=dev,ssl

server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12  # Certificate location
    key-store-password: changeit       # Certificate password
    key-store-type: PKCS12             # Certificate type
    key-alias: tomcat                  # Certificate alias
    
    # SSL Protocol Configuration
    protocol: TLS
    enabled-protocols: TLSv1.2, TLSv1.3
    
  # HTTP port (disabled when SSL is enabled)
  port: 8080  # This becomes HTTPS port when SSL is enabled
```

## 🚀 Running the Application with SSL

### Option 1: Using IntelliJ Run Configurations

#### Dev Environment with SSL
Create or use this run configuration (`.run/Dev with SSL (for OAuth).run.xml`):

```xml
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="Dev with SSL (for OAuth)" type="SpringBootApplicationConfigurationType" factoryName="Spring Boot">
    <option name="ACTIVE_PROFILES" value="dev,ssl" />
    <module name="InstagramPlatform" />
    <option name="SPRING_BOOT_MAIN_CLASS" value="com.sm.instagram.platform.InstagramPlatformApplication" />
    <option name="VM_PARAMETERS" value="-Dspring.profiles.active=dev,ssl -Dserver.port=8080 -Dfile.encoding=UTF-8" />
    <option name="WORKING_DIRECTORY" value="file://$PROJECT_DIR$" />
    <method v="2">
      <option name="Make" enabled="true" />
    </method>
  </configuration>
</component>
```

#### No-Redis Environment with SSL
Create or use this run configuration (`.run/InstagramPlatformApplication (No Redis with SSL).run.xml`):

```xml
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="InstagramPlatformApplication (No Redis with SSL)" type="SpringBootApplicationConfigurationType" factoryName="Spring Boot">
    <option name="ACTIVE_PROFILES" value="no-redis,ssl" />
    <module name="InstagramPlatform" />
    <option name="SPRING_BOOT_MAIN_CLASS" value="com.sm.instagram.platform.InstagramPlatformApplication" />
    <method v="2">
      <option name="Make" enabled="true" />
    </method>
  </configuration>
</component>
```

#### Production Standalone with SSL (CAREFUL - PROD DB!)
```xml
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="CheckItOut - Prod Standalone with SSL" type="SpringBootApplicationConfigurationType" factoryName="Spring Boot">
    <option name="ACTIVE_PROFILES" value="prod-standalone,ssl" />
    <option name="ALTERNATIVE_JRE_PATH" value="$USER_HOME$/.jdks/corretto-21.0.7" />
    <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="true" />
    <envs>
      <env name="DATABASE_PASSWORD" value="YOUR_PROD_DB_PASSWORD_HERE" />
      <env name="POSTGRES_PASSWORD" value="YOUR_PROD_DB_PASSWORD_HERE" />
      <env name="INSTAGRAM_CLIENT_SECRET" value="test-secret-for-local" />
      <env name="FIREBASE_PROJECT_ID" value="check-it-out-prod" />
      <env name="JAVA_TOOL_OPTIONS" value="-Xmx2g -Xms1g" />
    </envs>
    <module name="InstagramPlatform" />
    <option name="SPRING_BOOT_MAIN_CLASS" value="com.sm.instagram.platform.InstagramPlatformApplication" />
    <option name="VM_PARAMETERS" value="-Dspring.profiles.active=prod-standalone,ssl -Dspring.profiles.default=prod-standalone -Dserver.port=8080 -Dfile.encoding=UTF-8 -Dfirebase.project.id=check-it-out-prod" />
    <option name="WORKING_DIRECTORY" value="file://$PROJECT_DIR$" />
    <method v="2">
      <option name="Make" enabled="true" />
    </method>
  </configuration>
</component>
```

### Option 2: Using Maven Command Line

```bash
# Dev environment with SSL
mvn spring-boot:run -Dspring.profiles.active=dev,ssl

# No-Redis environment with SSL
mvn spring-boot:run -Dspring.profiles.active=no-redis,ssl

# Production standalone with SSL (CAREFUL!)
mvn spring-boot:run -Dspring.profiles.active=prod-standalone,ssl
```

### Option 3: Using Gradle (if applicable)

```bash
# Dev environment with SSL
./gradlew bootRun --args='--spring.profiles.active=dev,ssl'
```

## 🌐 Accessing the Application

1. **Start the application** using one of the methods above
2. **Open browser** and navigate to: `https://localhost:8080`
3. **Handle browser warning:**
   - Chrome: Click "Advanced" → "Proceed to localhost (unsafe)"
   - Firefox: Click "Advanced" → "Accept the Risk and Continue"
   - Edge: Click "Advanced" → "Continue to localhost (unsafe)"

## 🔍 Testing OAuth Flow

### 1. Verify HTTPS is working:
```bash
curl -k https://localhost:8080/api/actuator/health
```

### 2. Check OAuth endpoint:
```bash
curl -k -I https://localhost:8080/api/auth/social/callback/instagram
```

### 3. Test full OAuth flow:
1. Open frontend at `http://localhost:4200`
2. Click "Login with Instagram"
3. You'll be redirected to Instagram OAuth
4. After authorization, Instagram redirects to `https://localhost:8080/api/auth/social/callback/instagram`
5. Backend processes the code and redirects back to frontend with JWT

## 📋 OAuth Redirect URIs Configuration

### Local Development (with SSL):
```
https://localhost:8080/api/auth/social/callback/instagram
```

### Test Environment:
```
https://app.check-it-out.pl/api/auth/social/callback/instagram
https://www.app.check-it-out.pl/api/auth/social/callback/instagram
```

### Production Environment:
```
https://checkitout.app/api/auth/social/callback/instagram
https://www.checkitout.app/api/auth/social/callback/instagram
```

## 🛡️ Security Notes

- The `keystore.p12` file contains private keys - **DO NOT commit to git**
- The file is already in `.gitignore`
- Each developer should generate their own certificate
- The password "changeit" is standard for development - use stronger passwords in production
- Self-signed certificates are fine for local development

## ❓ Troubleshooting

### Certificate not found error:
```
Caused by: java.io.FileNotFoundException: class path resource [keystore.p12] cannot be resolved to URL
```
**Solution:** Ensure `keystore.p12` is in `src/main/resources/`

### Wrong password error:
```
Caused by: java.security.UnrecoverableKeyException: Password verification failed
```
**Solution:** Make sure you're using password "changeit" or update `application-ssl.yml`

### Port already in use:
```
Web server failed to start. Port 8080 was already in use.
```
**Solution:** Stop other applications using port 8080 or change the port in configuration

### Browser shows "Not Secure" warning:
**This is normal** for self-signed certificates. Click through the warning for local development.

### OAuth redirect fails with SSL error:
**Solution:** Make sure the Meta Dashboard has the HTTPS URL with correct port:
- `https://localhost:8080/api/auth/social/callback/instagram` (not HTTP)

## 📚 Additional Resources

- [Spring Boot SSL Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.webserver.configure-ssl)
- [Java Keytool Documentation](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
- [Instagram OAuth Documentation](https://developers.facebook.com/docs/instagram-basic-display-api/guides/getting-access-tokens-and-permissions)

## 🎯 Quick Checklist

- [ ] Generated `keystore.p12` using keytool
- [ ] Moved `keystore.p12` to `src/main/resources/`
- [ ] Updated/created IntelliJ run configuration with `ssl` profile
- [ ] Started application with HTTPS enabled
- [ ] Accessed `https://localhost:8080` and accepted certificate warning
- [ ] Updated Meta Dashboard with HTTPS callback URL
- [ ] Tested OAuth flow end-to-end

## 📞 Support

If you encounter issues:
1. Check this documentation first
2. Verify certificate exists and is in correct location
3. Check application logs for SSL-related errors
4. Ask in team chat with error details

---
*Last updated: January 2025*
*Author: CheckItOut Backend Team*