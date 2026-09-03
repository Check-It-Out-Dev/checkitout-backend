


# Set up of remote vps. 
On remote vps there is nginx which is frontier which is serving FE as builded angular static files,

And also is working as reverse proxy for backend. I have all on one vps. I have one nginx.
Here is my nginx configuration: `deployment/config/nginx-refactored/nginx.conf`


# Meta TEST Instance informations:
Usage: For local, and test environment. Need to configure nginx to properly redirect callbacks to be on test.
Type of api we have configured: API setup with Instagram business login
## Meta app name:
checkitout - Test
## Instagram app name:
checkitout - Test-IG
## Applications IDs:
Meta: 770277702827785 (checkitout-Test) → Instagram: 2113860459101101 (checkitout-Test-IG)
## Secrets:
Meta: ffd9fbe0c3128f935e62692bba3f8f64 | IG: cb43dff26c22717ed0c49d8e3b425a3e
## Meta App domains:
[localhost, app.check-it-out.pl]
## Instagram business login:
Example how to construct URL: https://www.instagram.com/oauth/authorize?force_reauth=true&client_id=2113860459101101&redirect_uri=https://www.app.check-it-out.pl/api/auth/social/callback/instagram&response_type=code&scope=instagram_business_basic%2Cinstagram_business_manage_messages%2Cinstagram_business_manage_comments%2Cinstagram_business_content_publish%2Cinstagram_business_manage_insights
Business login settings.OAuth redirect URIs: [https://localhost:4200/auth/social/callback/instagram, https://www.app.check-it-out.pl/auth/social/callback/instagram, https://app.check-it-out.pl/auth/social/callback/instagram]

# Meta PROD Instance informations:
Usage prod: for PROD instance, nginx settings have to be properly configured, and app settings need to be properly configured.
Type of api we have configured: API setup with Instagram business login
## Meta app name:
checkitout
## Instagram app name:
check-it-out-IG
## Applications IDs:
Meta: 1404020194302324 (checkitout) → Instagram: 2658917770964963 (check-it-out-IG)
## Secrets:
Meta: 14d22bf4bf254cd5bdf12f8264892eaf | IG: 464063d333233a6c872155f30de23c32
## Meta App domains:
[localhost, app.check-it-out.pl, checkitout.app]
## Instagram business login:
Example how to construct URL: https://www.instagram.com/oauth/authorize?force_reauth=true&client_id=2658917770964963&redirect_uri=https://checkitout.app/auth/social/callback/instagram&response_type=code&scope=instagram_business_basic%2Cinstagram_business_manage_messages%2Cinstagram_business_manage_comments%2Cinstagram_business_content_publish%2Cinstagram_business_manage_insights
Business login settings.OAuth redirect URIs: [https://checkitout.app/auth/social/callback/instagram, https://www.checkitout.app/auth/social/callback/instagram]
