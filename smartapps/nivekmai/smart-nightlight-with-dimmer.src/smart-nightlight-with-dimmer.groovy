/**
 *  Copyright 2015 SmartThings
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
 *  Smart Nightlight
 *
 *  Author: SmartThings
 *
 */
definition(
    name: "Smart Nightlight With Dimmer",
    namespace: "nivekmai",
    author: "Kevin Christensen",
    description: "Turns on lights when it's dark and motion is detected. Turns lights off when it becomes light or some time after motion ceases. Sets dimmer level based on time of night so it's not too bright.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet-luminance.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet-luminance@2x.png"
)

preferences {
	section("Control these lights..."){
		input "lights", "capability.switchLevel", multiple: true, required: true
	}
	section("Turning on when it's dark and there's movement..."){
		input "motionSensor", "capability.motionSensor", title: "Where?"
	}
	section("And then off when it's light or there's been no movement for..."){
		input "delayMinutes", "number", title: "Minutes?"
	}
	section("Using either on this light sensor (optional) or the local sunrise and sunset"){
		input "lightSensor", "capability.illuminanceMeasurement", required: false
	}
	section ("Sunrise offset (optional)...") {
		input "sunriseOffsetValue", "text", title: "HH:MM", required: false
		input "sunriseOffsetDir", "enum", title: "Before or After", required: false, options: ["Before","After"]
	}
	section ("Sunset offset (optional)...") {
		input "sunsetOffsetValue", "text", title: "HH:MM", required: false
		input "sunsetOffsetDir", "enum", title: "Before or After", required: false, options: ["Before","After"]
	}
	section ("Zip code (optional, defaults to location coordinates when location services are enabled)...") {
		input "zipCode", "text", title: "Zip code", required: false
	}
    section("By default, set the light level to...") {
    	input "defaultLightLevel", "number", title: "X% brightness", required: true
    }
    section("After...") {
    	input "lightTimeOne", "time", title: "HH:MM", required: false
    }
    section("Set the dimmer to...") {
        input "lightLevelOne", "number", title: "X% brightness", required: false
    }
    section("And then after...") {
    	input "lightTimeTwo", "time", title: "HH:MM", required: false
    }
    section("Set the dimmer to...") {
        input "lightLevelTwo", "number", title: "X% brightness", required: false
    }
    section("And then after...") {
    	input "lightTimeThree", "time", title: "HH:MM", required: false
    }
    section("Set the dimmer to...") {
        input "lightLevelThree", "number", title: "X% brightness", required: false
    }
    section("And then after...") {
    	input "lightTimeFour", "time", title: "HH:MM", required: false
    }
    section("Set the dimmer to...") {
        input "lightLevelFour", "number", title: "X% brightness", required: false
    }
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
	subscribe(motionSensor, "motion", motionHandler)
	if (lightSensor) {
		subscribe(lightSensor, "illuminance", illuminanceHandler, [filterEvents: false])
	}
	else {
		subscribe(location, "position", locationPositionChange)
		subscribe(location, "sunriseTime", sunriseSunsetTimeHandler)
		subscribe(location, "sunsetTime", sunriseSunsetTimeHandler)
		astroCheck()
	}
}

def locationPositionChange(evt) {
	log.trace "locationChange()"
	astroCheck()
}

def sunriseSunsetTimeHandler(evt) {
	state.lastSunriseSunsetEvent = now()
	log.debug "SmartNightlight.sunriseSunsetTimeHandler($app.id)"
	astroCheck()
}

def motionHandler(evt) {
	log.debug "$evt.name: $evt.value"
	if (evt.value == "active") {
		if (enabled()) {
			log.debug "turning on lights due to motion"
			lights.setLevel(getDimLevel())
			state.lastStatus = "on"
		}
		state.motionStopTime = null
	}
	else {
		state.motionStopTime = now()
		if(delayMinutes) {
			runIn(delayMinutes*60, turnOffMotionAfterDelay, [overwrite: false])
		} else {
			turnOffMotionAfterDelay()
		}
	}
}

def illuminanceHandler(evt) {
	log.debug "$evt.name: $evt.value, lastStatus: $state.lastStatus, motionStopTime: $state.motionStopTime"
	def lastStatus = state.lastStatus
	if (lastStatus != "off" && evt.integerValue > 50) {
		lights.setLevel(0)
		state.lastStatus = "off"
	}
	else if (state.motionStopTime) {
		if (lastStatus != "off") {
			def elapsed = now() - state.motionStopTime
			if (elapsed >= ((delayMinutes ?: 0) * 60000L) - 2000) {
				lights.setLevel(0)
				state.lastStatus = "off"
			}
		}
	}
	else if (lastStatus != "on" && evt.integerValue < 30){
		lights.setLevel(getDimLevel())
		state.lastStatus = "on"
	}
}

def turnOffMotionAfterDelay() {
	log.trace "In turnOffMotionAfterDelay, state.motionStopTime = $state.motionStopTime, state.lastStatus = $state.lastStatus"
	if (state.motionStopTime && state.lastStatus != "off") {
		def elapsed = now() - state.motionStopTime
        log.trace "elapsed = $elapsed"
		if (elapsed >= ((delayMinutes ?: 0) * 60000L) - 2000) {
        	log.debug "Turning off lights"
			lights.setLevel(0)
			state.lastStatus = "off"
		}
	}
}

def scheduleCheck() {
	log.debug "In scheduleCheck - skipping"
	//turnOffMotionAfterDelay()
}

def astroCheck() {
	def s = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: sunriseOffset, sunsetOffset: sunsetOffset)
	state.riseTime = s.sunrise.time
	state.setTime = s.sunset.time
	log.debug "rise: ${new Date(state.riseTime)}($state.riseTime), set: ${new Date(state.setTime)}($state.setTime)"
}

private enabled() {
	def result
	if (lightSensor) {
		result = lightSensor.currentIlluminance?.toInteger() < 30
	}
	else {
		def t = now()
		result = t < state.riseTime || t > state.setTime
	}
	result
}

private getSunriseOffset() {
	sunriseOffsetValue ? (sunriseOffsetDir == "Before" ? "-$sunriseOffsetValue" : sunriseOffsetValue) : null
}

private getSunsetOffset() {
	sunsetOffsetValue ? (sunsetOffsetDir == "Before" ? "-$sunsetOffsetValue" : sunsetOffsetValue) : null
}

private getDimLevel() {
	log.debug "Finding dim level"
	def result = defaultLightLevel
    def compareTime
    def theTime = now()
    if(lightTimeOne && lightLevelOne ) {
        compareTime = timeToday(lightTimeOne, location.timeZone)
        log.trace "lightTimeOne: $lightTimeOne"
        log.trace "compareTime: $compareTime"
        log.trace "theTime: $theTime"
    	if(theTime > compareTime.time) {
    		result = lightLevelOne
        }
    }
    if(lightTimeTwo && lightLevelTwo ) {
    	compareTime = timeToday(lightTimeTwo, location.timeZone)
        log.trace "lightTimeTwo: $lightTimeTwo"
        log.trace "compareTime: $compareTime"
        log.trace "theTime: $theTime"
    	if(theTime > compareTime.time) {
    		result = lightLevelTwo
        }
    }
    if(lightTimeThree && lightLevelThree ) {
    	compareTime = timeToday(lightTimeThree, location.timeZone)
        log.trace "lightTimeThree: $lightTimeThree"
        log.trace "compareTime: $compareTime"
        log.trace "theTime: $theTime"
    	if(theTime > compareTime.time) {
    		result = lightLevelThree
        }
    }
    if(lightTimeFour && lightLevelFour) {
    	compareTime = timeToday(lightTimeFour, location.timeZone)
        log.trace "lightTimeFour: $lightTimeFour"
        log.trace "compareTime: $compareTime"
        log.trace "theTime: $theTime"
    	if(theTime > compareTime.time) {
    		result = lightLevelFour
        }
    }
    log.debug "Dim level is $result"
    result
}