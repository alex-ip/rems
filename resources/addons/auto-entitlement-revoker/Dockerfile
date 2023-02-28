FROM python:3.7-alpine

COPY requirements.txt /auto_entitlement_revoker/
WORKDIR /auto_entitlement_revoker

RUN pip3 install -r requirements.txt

COPY . /auto_entitlement_revoker
EXPOSE 3009
CMD ["./auto_entitlement_revoker.py"]
