const testing = require('@firebase/rules-unit-testing');
const fs = require('fs');

// Load the rules
const rules = fs.readFileSync('firebase-storage.rules', 'utf8');

describe('Firebase Storage Security Rules', () => {
  let testEnv;
  
  beforeAll(async () => {
    testEnv = await testing.initializeTestEnvironment({
      projectId: 'test-project',
      storage: {
        rules,
        host: 'localhost',
        port: 9199
      }
    });
  });
  
  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });
  
  afterEach(async () => {
    if (testEnv) {
      await testEnv.clearStorage();
    }
  });
  
  describe('Public Read Access', () => {
    test('Anyone can read files', async () => {
      const unauthedStorage = testEnv.unauthenticatedContext().storage();
      
      // First create a file to test reading
      const adminStorage = testEnv.authenticatedContext('admin', { admin: true }).storage();
      const ref = adminStorage.ref('content/user123/photo.jpg');
      
      await testing.assertSucceeds(
        ref.put(new Uint8Array(1024), { contentType: 'image/jpeg' })
      );
      
      // Now test that anyone can read it
      const unauthedRef = unauthedStorage.ref('content/user123/photo.jpg');
      await testing.assertSucceeds(unauthedRef.getDownloadURL());
    });
  });
  
  describe('Upload Validation', () => {
    test('Authenticated users can upload valid images', async () => {
      const userId = 'user123';
      const authedStorage = testEnv.authenticatedContext(userId).storage();
      const ref = authedStorage.ref(`content/${userId}/photo.jpg`);
      
      const validFile = {
        contentType: 'image/jpeg',
        customMetadata: {
          uploadedBy: userId,
          uploadTimestamp: Date.now().toString()
        }
      };
      
      await testing.assertSucceeds(
        ref.put(new Uint8Array(1024), validFile)
      );
    });
    
    test('Cannot upload files exceeding size limit', async () => {
      const userId = 'user123';
      const authedStorage = testEnv.authenticatedContext(userId).storage();
      const ref = authedStorage.ref(`content/${userId}/large.jpg`);
      
      const largeFile = {
        contentType: 'image/jpeg',
        customMetadata: {
          uploadedBy: userId,
          uploadTimestamp: Date.now().toString()
        }
      };
      
      // 11MB file (exceeds 10MB limit)
      const largeData = new Uint8Array(11 * 1024 * 1024);
      
      await testing.assertFails(
        ref.put(largeData, largeFile)
      );
    });
    
    test('Cannot upload non-image files', async () => {
      const userId = 'user123';
      const authedStorage = testEnv.authenticatedContext(userId).storage();
      const ref = authedStorage.ref(`content/${userId}/document.pdf`);
      
      const pdfFile = {
        contentType: 'application/pdf',
        customMetadata: {
          uploadedBy: userId,
          uploadTimestamp: Date.now().toString()
        }
      };
      
      await testing.assertFails(
        ref.put(new Uint8Array(1024), pdfFile)
      );
    });
    
    test('Cannot upload to another users folder', async () => {
      const userId = 'user123';
      const otherUserId = 'user456';
      const authedStorage = testEnv.authenticatedContext(userId).storage();
      const ref = authedStorage.ref(`content/${otherUserId}/photo.jpg`);
      
      const file = {
        contentType: 'image/jpeg',
        customMetadata: {
          uploadedBy: userId,
          uploadTimestamp: Date.now().toString()
        }
      };
      
      await testing.assertFails(
        ref.put(new Uint8Array(1024), file)
      );
    });
    
    test('Unauthenticated users cannot upload', async () => {
      const unauthedStorage = testEnv.unauthenticatedContext().storage();
      const ref = unauthedStorage.ref('content/anonymous/photo.jpg');
      
      await testing.assertFails(
        ref.put(new Uint8Array(1024), { contentType: 'image/jpeg' })
      );
    });
  });
  
  describe('Deletion Protection', () => {
    test('Users cannot delete files directly', async () => {
      const userId = 'user123';
      const authedStorage = testEnv.authenticatedContext(userId).storage();
      const ref = authedStorage.ref(`content/${userId}/photo.jpg`);
      
      // First upload a file using admin privileges to bypass rules
      const adminStorage = testEnv.authenticatedContext('admin', { admin: true }).storage();
      const adminRef = adminStorage.ref(`content/${userId}/photo.jpg`);
      
      await testing.assertSucceeds(
        adminRef.put(new Uint8Array(1024), { contentType: 'image/jpeg' })
      );
      
      // Then try to delete it as the user
      await testing.assertFails(ref.delete());
    });
  });
});
