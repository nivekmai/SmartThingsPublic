/**
 *  Button press door lock
 *
 *  Copyright 2016 Kevin Christensen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Button press door lock",
    namespace: "nivekmai",
    author: "Kevin Christensen",
    description: "Lock or unlock a door when a switch is turned on or off.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("When this switch is switched...") {
		input "switch1", "capability.switch"
	}
    section("Lock the lock...") {
		input "lock1","capability.lock", multiple: true
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(switch1, "switch.on", onHandler)
	subscribe(switch1, "switch.off", offHandler)
	subscribeToCommand(switch1, "on", onHandler)
    subscribeToCommand(switch1, "off", offHandler)

}

// TODO: implement event handlers

def onHandler(evt){
	log.debug('on, lock')
	lock1.lock()
}

def offHandler(evt){
    log.debug(evt)
	log.debug('off, unlock')
	lock1.unlock()
}