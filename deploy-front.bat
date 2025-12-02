@echo off
echo [1/3] Frontend Building...
cd novel-insight-front
call docker build -t novel-front-prod .

echo [2/3] Removing old container...
call docker rm -f front-prod

echo [3/3] Running new container...
call docker run -d -p 80:80 --name front-prod novel-front-prod

echo [Done] Deployment Complete!
pause