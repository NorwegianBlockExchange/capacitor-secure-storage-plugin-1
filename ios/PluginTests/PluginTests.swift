import XCTest
import Capacitor

@testable import Plugin

// TODO: specific error messsages


class PluginTests: XCTestCase {
    
    func testSetAndGet() {
        let key = "key"
        let value = "Hello, World!"
        
        let plugin = SecureStoragePlugin()
        
        let callForSet = CAPPluginCall(
            callbackId: "testSet",
            options: [
                "key": key,
                "value": value
            ],
            success: { (result, call) in
                let resultValue = result!.data?["value"] as? Bool
                XCTAssertTrue(resultValue ?? false)
            },
            error: { (err) in
                let errorString: String? = err?.message
                print(errorString ?? "Unknown error")
                XCTFail("Error shouldn't have been called")
            }
        )
        
        plugin.set(callForSet!)
        
        sleep(1)
                
        let callForGet = CAPPluginCall(
            callbackId: "testGet",
            options: [
                "key": key,
                "prompt": "lorem ipsum"
            ],
            success: { (result, call) in
                let resultValue = result!.data?["value"] as? String
                XCTAssertTrue(resultValue == value)
            },
            error: { (err) in
                let errorString: String? = err?.message
                print(errorString ?? "Unknown error")
                XCTFail("Error shouldn't have been called")
            }
        );

        plugin.get(callForGet!)
    }
    
//    func testGet() {
//        let key = "key"
//        let value = "Hello, World!"
//        let keychainwrapper = setupDedicatedWrapper()
//        keychainwrapper.set(value, forKey: key)
//
//        let plugin = SecureStoragePlugin()
//
//        let call = CAPPluginCall(callbackId: "test", options: [
//            "key": key
//            ], success: { (result, call) in
//                let resultValue = result!.data?["value"] as? String
//                XCTAssertEqual(value, resultValue)
//        }, error: { (err) in
//            XCTFail("Error shouldn't have been called")
//        })
//
//        plugin.get(call!)
//    }
    
    func testNonExistingGet() {
        let key = "keyNonExisting"
        let plugin = SecureStoragePlugin()

        let call = CAPPluginCall(
            callbackId: "test",
            options: [
                "key": key
            ],
            success: { (result, call) in
                XCTFail("Success shouldn't have been called")
            },
            error: { (err) in
                XCTAssertNotNil(err)
            }
        )

        plugin.get(call!)
    }

    func testNonExistingRemove() {
        let key = "keyNonExisting"
        let plugin = SecureStoragePlugin()

        let call = CAPPluginCall(
            callbackId: "test",
            options: [
                "key": key
            ],
            success: { (result, call) in
                XCTFail("Success shouldn't have been called")
            },
            error: { (err) in
                XCTAssertNotNil(err)
            }
        )

        plugin.remove(call!)
    }
    
//
//    // same as testRemoveBoth, but don't prefill standard wrapper
//    func testRemove() {
//        let key = "key"
//        let value = "Hello, World!"
//        // prefill dedicated keychain wrapper
//        let keychainwrapper = setupDedicatedWrapper()
//        keychainwrapper.set(value, forKey: key)
//
//        let plugin = SecureStoragePlugin()
//
//        let call = CAPPluginCall(callbackId: "test", options: [
//            "key": key
//            ], success: { (result, call) in
//                let resultValue = result!.data?["value"] as? Bool
//                XCTAssertTrue(resultValue ?? false)
//                // dedicated keychain wrapper
//                let dedicatedValue = keychainwrapper.string(forKey: key)
//                XCTAssertNil(dedicatedValue)
//        }, error: { (err) in
//            XCTFail("Error shouldn't have been called")
//        })
//
//        plugin.remove(call!)
//    }
    
    func testGetPlatform() {
        let plugin = SecureStoragePlugin()
        let call = CAPPluginCall(
            callbackId: "test",
            success: { (result, call) in
                let resultValue = result!.data?["value"] as? String
                XCTAssertEqual("ios", resultValue)
            },
            error: { (err) in
                XCTFail("Error shouldn't have been called")
            }
        )
        plugin.getPlatform(call!)
    }
    
}
