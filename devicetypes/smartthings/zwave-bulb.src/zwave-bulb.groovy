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

 */

metadata {

	definition (name: "Z-Wave Bulb", namespace: "smartthings", author: "SmartThings") {

		capability "Actuator"

		capability "Switch"

		capability "Switch Level"

		capability "Refresh"

		capability "Sensor"



		fingerprint deviceId: "0x"

		fingerprint deviceId: "0x3101"  // for z-wave certification, can remove these when sub-meters/window-coverings are supported

		fingerprint deviceId: "0x3101", inClusters: "0x86,0x32"

		fingerprint deviceId: "0x09", inClusters: "0x86,0x72,0x26"

		fingerprint deviceId: "0x0805", inClusters: "0x47,0x86,0x72"

	}



	simulator {

		status "on":  "command: 2003, payload: FF"

		status "off": "command: 2003, payload: 00"

	}



		// UI tile definitions

    tiles(scale: 2) {

        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){

            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {

                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#79b821", nextState:"turningOff"

                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"

                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#79b821", nextState:"turningOff"

                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"

            }

            tileAttribute ("device.level", key: "SLIDER_CONTROL") {

                attributeState "level", action:"switch level.setLevel"

            }

        }

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {

            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"

        }

        main "switch"

        details(["switch", "refresh"])

	}

}



def parse(String description) {

	def result = []

	if (description.startsWith("Err")) {

	    result = createEvent(descriptionText:description, isStateChange:true)

	} else {

		def cmd = zwave.parse(description, [0x20: 1, 0x84: 1, 0x98: 1, 0x56: 1, 0x60: 3])

		if (cmd) {

			result += zwaveEvent(cmd)

		}

	}

	return result

}



def updated() {

	response(zwave.wakeUpV1.wakeUpNoMoreInformation())

}



def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd) {

	[ createEvent(descriptionText: "${device.displayName} woke up"),

	  response(zwave.wakeUpV1.wakeUpNoMoreInformation()) ]

}



def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {

	if (cmd.value == 0) {

		createEvent(name: "switch", value: "off")

	} else if (cmd.value == 255) {

		createEvent(name: "switch", value: "on")

	} else {

		[ createEvent(name: "switch", value: "on"), createEvent(name: "switchLevel", value: cmd.value) ]

	}

}



def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {

	def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x84: 1])

	if (encapsulatedCommand) {

		state.sec = 1

		def result = zwaveEvent(encapsulatedCommand)

		result = result.collect {

			if (it instanceof physicalgraph.device.HubAction && !it.toString().startsWith("9881")) {

				response(cmd.CMD + "00" + it.toString())

			} else {

				it

			}

		}

		result

	}

}



def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd) {

	def versions = [0x31: 2, 0x30: 1, 0x84: 1, 0x9C: 1, 0x70: 2]

	// def encapsulatedCommand = cmd.encapsulatedCommand(versions)

	def version = versions[cmd.commandClass as Integer]

	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)

	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)

	if (encapsulatedCommand) {

		zwaveEvent(encapsulatedCommand)

	}

}



def zwaveEvent(physicalgraph.zwave.Command cmd) {

	createEvent(descriptionText: "$device.displayName: $cmd", isStateChange: true)

}



def on() {

	commands([zwave.basicV1.basicSet(value: 0xFF), zwave.basicV1.basicGet()])

}



def off() {

	commands([zwave.basicV1.basicSet(value: 0x00), zwave.basicV1.basicGet()])

}



def refresh() {

	command(zwave.basicV1.basicGet())

}



def setLevel(value) {

	commands([zwave.basicV1.basicSet(value: value as Integer), zwave.basicV1.basicGet()], 4000)

}



private command(physicalgraph.zwave.Command cmd) {

	if (state.sec) {

		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()

	} else {

		cmd.format()

	}

}



private commands(commands, delay=200) {

	delayBetween(commands.collect{ command(it) }, delay)

}