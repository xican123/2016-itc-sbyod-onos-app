### SETUP
Use `$ vagrant up` to create a VM with ONOS, the Sardine-BYOD application
and a Mininet topology preinstalled.

### START
Log in to the VM via `$ vagrant ssh`

1. Inside the VM start ONOS by executing `$ onos-karaf clean`
   
2. Start the Mininet topology via `$ sudo ./mininetSBYOD.py` (make it executable)

3. Install the S-BYOD app in ONOS by executing
   `$ onos-app localhost install! 2016-itc-sbyod-onos-app/target/sbyod-1.0-SNAPSHOT.jar`